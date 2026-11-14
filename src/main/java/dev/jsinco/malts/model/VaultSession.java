package dev.jsinco.malts.model;

import dev.jsinco.malts.Malts;
import dev.jsinco.malts.logging.MaltsLogger;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.utility.BorrowedItems;
import dev.jsinco.malts.utility.Executors;
import dev.jsinco.malts.utility.InternalScheduler;
import dev.jsinco.malts.utility.Text;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

// A single authoritative in-memory copy of a vault while it's open or being saved
public final class VaultSession {

    @Getter private final VaultKey key;
    private final ReentrantLock lock = new ReentrantLock();
    private final CompletableFuture<VaultSession> ready = new CompletableFuture<>();

    // All guarded by lock
    private final Map<UUID, Viewer> viewers = new LinkedHashMap<>();
    private Vault master;
    private Vault pendingMeta;
    private int holds;
    private int pendingSaves;
    private boolean dirty;
    private boolean dead;
    private boolean closing;

    private CompletableFuture<Void> saveChain = CompletableFuture.completedFuture(null);

    // Completes once this session is removed from VaultSessions (= when it's saved and nobody uses it anymore)
    private final CompletableFuture<Void> removed = new CompletableFuture<>();

    private static final class Viewer {
        private final Player player;
        private final Vault view;
        private final AtomicBoolean refreshQueued = new AtomicBoolean();

        // Net amount of each item this viewer put into (+) or took out of (-) the vault (for logging)
        private final Map<ItemStack, Integer> netChanges = new HashMap<>();

        private Viewer(Player player, Vault view) {
            this.player = player;
            this.view = view;
        }
    }

    public enum JoinResult {
        OPENED, ALREADY_OPEN, CLOSING, FAILED
    }

    VaultSession(VaultKey key) {
        this.key = key;
    }

    CompletableFuture<VaultSession> ready() {
        return ready;
    }

    boolean hold() {
        lock.lock();
        try {
            if (dead) {
                return false;
            }
            holds++;
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void release() {
        lock.lock();
        try {
            holds--;
        } finally {
            lock.unlock();
        }
        VaultSessions.tryRemove(this);
    }

    void loaded(Vault vault) {
        lock.lock();
        try {
            master = vault;
            if (pendingMeta != null) {
                master.copyMetaFrom(pendingMeta);
                pendingMeta = null;
                dirty = true;
            }
        } finally {
            lock.unlock();
        }
        ready.complete(this);
    }

    void failed(Throwable throwable) {
        ready.completeExceptionally(throwable);
    }

    boolean tryKill() {
        lock.lock();
        try {
            boolean removable = ready.isDone() && viewers.isEmpty() && holds <= 0 && pendingSaves == 0 && !dirty;
            if (removable) {
                dead = true;
            }
            return removable;
        } finally {
            lock.unlock();
        }
    }

    boolean isLockHeldByCurrentThread() {
        return lock.isHeldByCurrentThread();
    }

    boolean isDead() {
        lock.lock();
        try {
            return dead;
        } finally {
            lock.unlock();
        }
    }

    CompletableFuture<Void> removed() {
        return removed;
    }

    CompletableFuture<Void> close() {
        List<Viewer> current;
        lock.lock();
        try {
            closing = true;
            current = new ArrayList<>(viewers.values());
        } finally {
            lock.unlock();
        }
        for (Viewer viewer : current) {
            kick(viewer);
        }
        VaultSessions.tryRemove(this);
        return removed;
    }

    private void kick(Viewer viewer) {
        Player player = viewer.player;
        Runnable close = () -> {
            if (player.getOpenInventory().getTopInventory() == viewer.view.getInventory()) {
                player.closeInventory();
            }
            // Normally done by the close event, but not if the view was no longer open
            leave(player, viewer.view);
        };

        if (Bukkit.isOwnedByCurrentRegion(player)) {
            close.run();
            return;
        }
        ScheduledTask task = null;
        try {
            // If the player is gone before this runs, their view can't be clicked anymore, so just unregister it
            task = player.getScheduler().run(Malts.getInstance(), t -> close.run(), () -> leave(player, viewer.view));
        } catch (Throwable throwable) {
            Text.debug("Could not schedule closing vault " + key.owner() + " #" + key.id() + " for " + player.getName() + ": " + throwable);
        }
        if (task == null) {
            leave(player, viewer.view);
        }
    }

    // Closes the vault for everyone and unregisters every viewer immediately (called on shutdown)
    void closeNow() {
        List<Viewer> current;
        lock.lock();
        try {
            closing = true;
            current = new ArrayList<>(viewers.values());
        } finally {
            lock.unlock();
        }
        for (Viewer viewer : current) {
            Player player = viewer.player;
            try {
                leave(player, viewer.view);
                if (player.getOpenInventory().getTopInventory() == viewer.view.getInventory()) {
                    closeAfterDisable(player, viewer.view);
                }
            } catch (Throwable throwable) {
                Text.error("Could not close vault " + key.owner() + " #" + key.id() + " for " + player.getName(), throwable);
                leave(player, viewer.view);
            }
        }
    }

    private void closeAfterDisable(Player player, Vault view) {
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            player.closeInventory();
            return;
        }

        // The player is in another Folia region, so we can't synchronously close their inventory
        // What we can do is empty it from this thread, so the items are not duplicated
        view.getInventory().clear();

        // Now close the inventory on the player's thread using Folia's internal scheduler
        boolean scheduled = InternalScheduler.schedule(player, () -> {
            if (player.getOpenInventory().getTopInventory() != view.getInventory()) {
                return;
            }
            // Anything in the emptied view was put there after Malts was disabled -> give it back top the player
            List<ItemStack> leftovers = new ArrayList<>();
            for (ItemStack item : view.getInventory().getContents()) {
                if (item != null && !item.isEmpty()) {
                    leftovers.add(item);
                }
            }
            view.getInventory().clear();
            player.closeInventory();
            for (ItemStack item : player.getInventory().addItem(leftovers.toArray(new ItemStack[0])).values()) {
                player.getWorld().dropItem(player.getLocation(), item);
            }
        }, () -> {
        });
        if (scheduled) {
            return;
        }

        // Last resort: disconnecting closes the vault on the client, so nothing can be put into the emptied view
        Text.log("Kicking " + player.getName() + " to close vault " + key.owner() + " #" + key.id() + " while Malts is disabled");
        try {
            player.kick(Component.text("Vaults were closed because Malts was disabled. Your items are safe, please rejoin."));
        } catch (Throwable throwable) {
            Text.error("Could not kick " + player.getName() + " out of vault " + key.owner() + " #" + key.id(), throwable);
        }
    }

    boolean isDirty() {
        lock.lock();
        try {
            return dirty;
        } finally {
            lock.unlock();
        }
    }

    // A snapshot of the player currently viewing this vault
    public List<Player> viewerPlayers(@Nullable Player exclude) {
        lock.lock();
        try {
            List<Player> players = new ArrayList<>(viewers.size());
            for (Viewer viewer : viewers.values()) {
                if (exclude == null || !viewer.player.getUniqueId().equals(exclude.getUniqueId())) {
                    players.add(viewer.player);
                }
            }
            return players;
        } finally {
            lock.unlock();
        }
    }

    public boolean isViewing(Player player, Vault view) {
        lock.lock();
        try {
            Viewer viewer = viewers.get(player.getUniqueId());
            return viewer != null && viewer.view == view;
        } finally {
            lock.unlock();
        }
    }

    // Registers the player as a viewer and opens a fresh view for them
    // (Consumes one hold, ust be called on the player's own thread)
    JoinResult join(Player player) {
        Vault view;
        lock.lock();
        try {
            holds--;
            if (closing) {
                return JoinResult.CLOSING;
            }
            Viewer existing = viewers.get(player.getUniqueId());
            if (existing != null) {
                if (player.getOpenInventory().getTopInventory() == existing.view.getInventory()) {
                    return JoinResult.ALREADY_OPEN;
                }
                // Stale registration (the old view is no longer open)
                viewers.remove(player.getUniqueId());
            }
            BorrowedItems.refreshLore(master.getInventory());
            view = master.snapshot();
            viewers.put(player.getUniqueId(), new Viewer(player, view));
        } finally {
            lock.unlock();
            VaultSessions.tryRemove(this);
        }

        player.openInventory(view.getInventory());
        if (player.getOpenInventory().getTopInventory() != view.getInventory()) {
            // Opening was canceled by another plugin
            leave(player, view);
            return JoinResult.FAILED;
        }
        return JoinResult.OPENED;
    }

    // Unregisters the player's view and saves the vault (Must be called on the player's own thread)
    public void leave(Player player, Vault view) {
        Map<ItemStack, Integer> netChanges;
        lock.lock();
        try {
            Viewer viewer = viewers.get(player.getUniqueId());
            if (viewer == null || viewer.view != view) {
                return;
            }
            viewers.remove(player.getUniqueId());
            netChanges = new HashMap<>(viewer.netChanges);
        } finally {
            lock.unlock();
        }

        logChanges(player, view, netChanges);
        save();
    }

    // Runs an operation on the master copy of this vault (Must be called on the actor's thread)
    public boolean mutate(Player actor, Vault view, Consumer<Vault> operation) {
        Viewer self;
        List<Viewer> others = new ArrayList<>();
        ItemStack[] snapshot = null;
        lock.lock();
        try {
            self = viewers.get(actor.getUniqueId());
            if (self == null || self.view != view || master == null) {
                return false;
            }
            ItemStack[] before = cloneContents(master.getInventory().getContents());
            try {
                operation.accept(master);
            } finally {
                // Even if the operation failed halfway through, whatever it changed must be saved and shown
                ItemStack[] after = cloneContents(master.getInventory().getContents());
                if (recordChanges(self, before, after)) {
                    dirty = true;
                    snapshot = after;
                    for (Viewer viewer : viewers.values()) {
                        if (viewer != self) {
                            others.add(viewer);
                        }
                    }
                }
            }
        } finally {
            lock.unlock();
        }

        if (snapshot != null) {
            self.view.getInventory().setContents(snapshot);
            for (Viewer viewer : others) {
                queueRefresh(viewer);
            }
        }
        return true;
    }

    // Reads the master copy of this vault while holding the lock (The function must not modify anything)
    @Nullable
    public <T> T read(Function<Vault, T> function) {
        lock.lock();
        try {
            return master != null ? function.apply(master) : null;
        } finally {
            lock.unlock();
        }
    }

    private void queueRefresh(Viewer viewer) {
        if (viewer.refreshQueued.compareAndSet(false, true)) {
            Executors.runSync(viewer.player, () -> refresh(viewer));
        }
    }

    // Runs on the viewer's thread, always copying the newest master contents so refresh order doesn't matter
    private void refresh(Viewer viewer) {
        viewer.refreshQueued.set(false);
        ItemStack[] snapshot;
        lock.lock();
        try {
            if (viewers.get(viewer.player.getUniqueId()) != viewer) {
                return;
            }
            snapshot = cloneContents(master.getInventory().getContents());
        } finally {
            lock.unlock();
        }
        viewer.view.getInventory().setContents(snapshot);
    }

    // Applies the metadata of the given vault to the master copy and all open views
    public void applyMeta(Vault source) {
        Vault meta = source.snapshot();
        List<Viewer> current;
        lock.lock();
        try {
            if (master == null) {
                pendingMeta = meta;
                return;
            }
            master.copyMetaFrom(meta);
            dirty = true;
            current = new ArrayList<>(viewers.values());
        } finally {
            lock.unlock();
        }
        for (Viewer viewer : current) {
            Executors.runSync(viewer.player, () -> viewer.view.copyMetaFrom(meta));
        }
    }

    // Saves a snapshot of the current master copy (Saves are applied strictly in the order they are requested in)
    public CompletableFuture<Void> save() {
        CompletableFuture<Void> result;
        lock.lock();
        try {
            if (master == null || dead) {
                return CompletableFuture.completedFuture(null);
            }
            Vault snapshot = master.snapshot();
            dirty = false;
            pendingSaves++;
            result = saveChain
                    .thenCompose(unused -> DataSource.getInstance().saveVault(snapshot))
                    .exceptionally(throwable -> {
                        Text.error("Failed to save vault " + key.owner() + " #" + key.id(), throwable);
                        return null;
                    });
            saveChain = result;
        } finally {
            lock.unlock();
        }

        result.whenComplete((unused, throwable) -> {
            lock.lock();
            try {
                pendingSaves--;
            } finally {
                lock.unlock();
            }
            VaultSessions.tryRemove(this);
        });
        return result;
    }

    private boolean recordChanges(Viewer viewer, ItemStack[] before, ItemStack[] after) {
        boolean changed = false;
        boolean logging = MaltsLogger.logger().enabled();
        int length = Math.max(before.length, after.length);
        for (int i = 0; i < length; i++) {
            ItemStack b = i < before.length ? before[i] : null;
            ItemStack a = i < after.length ? after[i] : null;
            if (Objects.equals(empty(b), empty(a))) {
                continue;
            }
            changed = true;
            if (!logging) {
                continue;
            }
            if (b != null && a != null && !b.isEmpty() && !a.isEmpty() && b.isSimilar(a)) {
                viewer.netChanges.merge(b.asOne(), a.getAmount() - b.getAmount(), Integer::sum);
                continue;
            }
            if (b != null && !b.isEmpty()) {
                viewer.netChanges.merge(b.asOne(), -b.getAmount(), Integer::sum);
            }
            if (a != null && !a.isEmpty()) {
                viewer.netChanges.merge(a.asOne(), a.getAmount(), Integer::sum);
            }
        }
        return changed;
    }

    private static void logChanges(Player player, Vault view, Map<ItemStack, Integer> netChanges) {
        if (netChanges.isEmpty() || !MaltsLogger.logger().enabled()) {
            return;
        }
        List<ItemStack> removed = new ArrayList<>();
        List<ItemStack> added = new ArrayList<>();
        for (Map.Entry<ItemStack, Integer> entry : netChanges.entrySet()) {
            int amount = entry.getValue();
            List<ItemStack> target = amount > 0 ? added : removed;
            int remaining = Math.abs(amount);
            int max = Math.max(1, entry.getKey().getMaxStackSize());
            while (remaining > 0) {
                int chunk = Math.min(max, remaining);
                target.add(entry.getKey().asQuantity(chunk));
                remaining -= chunk;
            }
        }
        MaltsLogger.logger().logVaultChanges(player, view, removed.toArray(new ItemStack[0]), added.toArray(new ItemStack[0]));
    }

    @Nullable
    private static ItemStack empty(@Nullable ItemStack item) {
        return item == null || item.isEmpty() ? null : item;
    }

    static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null || contents[i].isEmpty() ? null : contents[i].clone();
        }
        return copy;
    }
}
