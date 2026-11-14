package dev.jsinco.malts.model;

import dev.jsinco.malts.Malts;
import dev.jsinco.malts.api.events.vault.VaultOpenEvent;
import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.files.Lang;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.utility.Executors;
import dev.jsinco.malts.utility.Text;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class VaultSessions {

    private static final long AUTOSAVE_SECONDS = 5;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 15;

    private static final long CLOSE_TIMEOUT_SECONDS = 30;

    // Never call into this map while holding a session lock
    private static final ConcurrentHashMap<VaultKey, VaultSession> SESSIONS = new ConcurrentHashMap<>();
    private static ScheduledTask autosaveTask;

    // Owners whose vaults can't be opened right now, with how many actions blocked them
    private static final Object BLOCK_MONITOR = new Object();
    private static final Map<UUID, Integer> BLOCKED_OWNERS = new HashMap<>();
    private static boolean closedForShutdown;

    private VaultSessions() {
    }

    @Nullable
    public static VaultSession get(VaultKey key) {
        return SESSIONS.get(key);
    }

    // Gets or creates the session of a vault and holds it so it can't be removed
    // The returned session must be released with VaultSession#release once done with
    // If loading fails, the returned future completes exceptionally and nothing needs to be released
    public static CompletableFuture<VaultSession> acquire(VaultKey key) {
        boolean[] created = {false};
        VaultSession session;
        synchronized (BLOCK_MONITOR) {
            if (closedForShutdown || BLOCKED_OWNERS.containsKey(key.owner())) {
                return CompletableFuture.failedFuture(new VaultsClosedException(key));
            }
            session = SESSIONS.compute(key, (k, existing) -> {
                if (existing != null && existing.hold()) {
                    return existing;
                }
                VaultSession fresh = new VaultSession(k);
                fresh.hold();
                created[0] = true;
                return fresh;
            });
        }

        if (created[0]) {
            // The session is registered before reading, nothing else can write this vault's contents meanwhile
            CompletableFuture<Vault> load;
            try {
                load = DataSource.getInstance().getVault(key.owner(), key.id(), true);
            } catch (Throwable throwable) {
                load = CompletableFuture.failedFuture(throwable);
            }
            load.whenComplete((vault, throwable) -> {
                if (throwable != null || vault == null) {
                    session.failed(throwable != null ? throwable : new IllegalStateException("Could not load vault " + key.owner() + " #" + key.id()));
                } else {
                    session.loaded(vault);
                }
            });
        }

        return session.ready().whenComplete((s, throwable) -> {
            if (throwable != null) {
                session.release();
            }
        });
    }

    static void tryRemove(VaultSession session) {
        if (session.isLockHeldByCurrentThread()) {
            return; // Would invert the lock order, whoever holds it will try again
        }
        SESSIONS.computeIfPresent(session.getKey(), (k, s) -> s == session && s.tryKill() ? null : s);
        if (session.isDead()) {
            session.removed().complete(null);
        }
    }

    /**
     * Runs an action that writes vaults of the given owners directly to the database, like a transfer, import, or delete.
     * First, nobody can open any vault of these owners anymore. Then, every open vault of theirs is closed for everyone
     * viewing it and saved. Only once all of that is done does the action run, so it can never be overwritten
     * by an open vault being saved later, and nobody can take items out of a vault the action is changing.
     *
     * @return the action's result, or a failed future if the vaults couldn't be closed in time
     */
    public static <T> CompletableFuture<T> withClosedVaults(Collection<UUID> owners, Supplier<CompletableFuture<T>> action) {
        Set<UUID> blocked = Set.copyOf(owners);
        List<VaultSession> open = new ArrayList<>();
        synchronized (BLOCK_MONITOR) { // so no session for these owners can be created after this
            for (UUID owner : blocked) {
                BLOCKED_OWNERS.merge(owner, 1, Integer::sum);
            }
            for (VaultSession session : SESSIONS.values()) {
                if (blocked.contains(session.getKey().owner())) {
                    open.add(session);
                }
            }
        }

        CompletableFuture<T> result;
        try {
            CompletableFuture<?>[] closed = open.stream().map(VaultSession::close).toArray(CompletableFuture[]::new);
            result = CompletableFuture.allOf(closed)
                    .orTimeout(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    // Never run the action on whichever region thread finished the last save
                    .thenComposeAsync(unused -> action.get(), runnable -> Executors.runAsync(task -> runnable.run()));
        } catch (Throwable throwable) {
            result = CompletableFuture.failedFuture(throwable);
        }

        return result.whenComplete((value, throwable) -> {
            synchronized (BLOCK_MONITOR) {
                for (UUID owner : blocked) {
                    BLOCKED_OWNERS.computeIfPresent(owner, (k, count) -> count > 1 ? count - 1 : null);
                }
            }
            if (throwable != null) {
                Text.error("Could not close the vaults of " + blocked + " before changing them", throwable);
            }
        });
    }

    // Thrown when trying to open or modify a vault while it's closed by #withClosedVaults or the plugin shutting down
    public static final class VaultsClosedException extends IllegalStateException {
        private VaultsClosedException(VaultKey key) {
            super("Vault " + key.owner() + " #" + key.id() + " can't be opened right now");
        }
    }

    // Opens a vault for a player or adds them to an existing session
    public static void open(Player player, Vault vault) {
        Lang lang = ConfigManager.get(Lang.class);
        acquire(vault.getKey()).whenComplete((session, throwable) -> {
            if (throwable != null) {
                if (!(unwrap(throwable) instanceof VaultsClosedException)) {
                    Text.error("Could not open vault " + vault.getOwner() + " #" + vault.getId() + " for " + player.getName(), throwable);
                }
                lang.entry(l -> l.vaults().databaseBusy(), player);
                return;
            }

            boolean scheduled = false;
            try {
                List<Player> viewers = session.viewerPlayers(player);
                Vault.State state = vault.canOpen(player, viewers);
                VaultOpenEvent event = new VaultOpenEvent(vault, player, viewers, !Bukkit.isPrimaryThread());
                event.setCancelled(!state.toBoolean());
                Text.debug("Player " + player.getName() + " is attempting to open vault " + vault.getId() + ". Can open: " + state);

                if (!event.callEvent()) {
                    if (state == Vault.State.DATABASE_BUSY) {
                        lang.entry(l -> l.vaults().databaseBusy(), player);
                    } else {
                        lang.entry(l -> l.vaults().alreadyOpen(), player);
                    }
                    return;
                }

                Runnable join = () -> {
                    VaultSession.JoinResult result = session.join(player);
                    if (result == VaultSession.JoinResult.ALREADY_OPEN) {
                        lang.entry(l -> l.vaults().alreadyOpen(), player);
                    } else if (result == VaultSession.JoinResult.CLOSING) {
                        lang.entry(l -> l.vaults().databaseBusy(), player);
                    }
                };

                if (Bukkit.isOwnedByCurrentRegion(player)) {
                    scheduled = true;
                    join.run();
                } else {
                    // The hold is consumed by join, or released if the player is gone before it runs
                    ScheduledTask task = player.getScheduler().run(Malts.getInstance(), t -> join.run(), session::release);
                    scheduled = task != null;
                }
            } finally {
                if (!scheduled) {
                    session.release();
                }
            }
        });
    }

    // Starts saving changed vaults periodically, so changes reach the database even while a vault stays open
    public static void start() {
        synchronized (BLOCK_MONITOR) {
            closedForShutdown = false;
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        autosaveTask = Executors.runRepeatingAsync(AUTOSAVE_SECONDS, AUTOSAVE_SECONDS, TimeUnit.SECONDS, task -> {
            for (VaultSession session : SESSIONS.values()) {
                if (session.isDirty()) {
                    session.save();
                }
            }
        });
    }

    // Saves every open vault and waits for all saves to finish
    public static void flush() {
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        for (VaultSession session : SESSIONS.values()) {
            saves.add(session.save());
        }
        try {
            CompletableFuture.allOf(saves.toArray(new CompletableFuture[0])).get(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            Text.error("Timed out or failed while saving open vaults", e);
        }
    }

    // Closes every open vault for everyone viewing it, saves them all and stops vaults from being opened again
    public static void shutdown() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        synchronized (BLOCK_MONITOR) {
            closedForShutdown = true;
        }
        // Nothing can be scheduled while the plugin is disabling, so close and unregister
        for (VaultSession session : SESSIONS.values()) {
            session.closeNow();
        }
        flush();
    }

    private static Throwable unwrap(Throwable throwable) {
        while (throwable instanceof CompletionException && throwable.getCause() != null) {
            throwable = throwable.getCause();
        }
        return throwable;
    }
}
