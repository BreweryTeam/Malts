package dev.jsinco.malts.events;

import dev.jsinco.malts.model.Vault;
import dev.jsinco.malts.model.VaultSession;
import dev.jsinco.malts.model.VaultSessions;
import dev.jsinco.malts.utility.BorrowedItems;
import dev.jsinco.malts.utility.Executors;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.DragType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Predicate;

// Handles all types of clicks where a vault is involved using the open vault's single master copy
public class VaultClickEngine implements Listener {

    private static final int OFFHAND_SLOT = 40;

    static Predicate<ItemStack> placeholderCheck = BorrowedItems::isPlaceholder;

    private static final int[] PLAYER_INSERT_ORDER = order(new int[]{8, 7, 6, 5, 4, 3, 2, 1, 0}, range(35, 9));
    private static final int[] PLAYER_MENU_ORDER = order(range(9, 35), range(0, 8));

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        if (!(view.getTopInventory().getHolder(false) instanceof Vault vaultView)) {
            return;
        }
        int topSize = view.getTopInventory().getSize();
        if (!touchesVault(event, topSize)) {
            return;
        }
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        VaultSession session = VaultSessions.get(vaultView.getKey());
        if (session != null) {
            int rawSlot = event.getRawSlot();
            boolean inTop = rawSlot < topSize;
            int slot = inTop ? rawSlot : view.convertSlot(rawSlot);
            ClickType click = event.getClick();
            int hotbarButton = event.getHotbarButton();
            session.mutate(player, vaultView, master -> {
                if (inTop) {
                    clickVault(player, master.getInventory(), master.isLockActive(), slot, click, hotbarButton);
                } else {
                    clickPlayer(player, master.getInventory(), master.isLockActive(), slot, click);
                }
            });
        }
        Executors.delayedSync(player, 1, player::updateInventory);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        InventoryView view = event.getView();
        if (!(view.getTopInventory().getHolder(false) instanceof Vault vaultView)) {
            return;
        }
        int topSize = view.getTopInventory().getSize();
        if (!touchesVault(event, topSize)) {
            return;
        }
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        List<DragTarget> targets = new ArrayList<>();
        for (int rawSlot : new TreeSet<>(event.getRawSlots())) {
            boolean inTop = rawSlot < topSize;
            targets.add(new DragTarget(inTop, inTop ? rawSlot : view.convertSlot(rawSlot)));
        }
        DragType type = event.getType();

        // A denied drag resets the cursor after this event -> do that afterward from the then-current state
        Executors.delayedSync(player, 1, () -> {
            VaultSession session = VaultSessions.get(vaultView.getKey());
            if (session != null && player.getOpenInventory().getTopInventory() == vaultView.getInventory()) {
                session.mutate(player, vaultView, master -> drag(player, master.getInventory(), master.isLockActive(), targets, type));
            }
            player.updateInventory();
        });
    }

    // Another plugin could have un-cancelled one of these:
    @EventHandler(priority = EventPriority.MONITOR)
    public void guardClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        if (!event.isCancelled() && view.getTopInventory().getHolder(false) instanceof Vault
                && touchesVault(event, view.getTopInventory().getSize())) {
            event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void guardDrag(InventoryDragEvent event) {
        InventoryView view = event.getView();
        if (!event.isCancelled() && view.getTopInventory().getHolder(false) instanceof Vault
                && touchesVault(event, view.getTopInventory().getSize())) {
            event.setCancelled(true);
        }
    }

    private static boolean touchesVault(InventoryClickEvent event, int topSize) {
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0) {
            return false; // Outside the window (only drops the cursor)
        }
        if (rawSlot < topSize) {
            return true;
        }
        return switch (event.getClick()) {
            case SHIFT_LEFT, SHIFT_RIGHT, DOUBLE_CLICK -> true;
            default -> false;
        };
    }

    private static boolean touchesVault(InventoryDragEvent event, int topSize) {
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                return true;
            }
        }
        return false;
    }


    // Clicks on a vault slot
    static void clickVault(Player player, Inventory inventory, boolean locked, int slot, ClickType click, int hotbarButton) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        // Locked vaults and placeholders are handled by the borrow listener
        if (locked || isPlaceholder(inventory.getItem(slot))) {
            return;
        }

        switch (click) {
            case LEFT -> pickup(player, inventory, slot, true);
            case RIGHT -> pickup(player, inventory, slot, false);
            case SHIFT_LEFT, SHIFT_RIGHT -> moveToPlayer(player, inventory, slot);
            case NUMBER_KEY -> {
                if (hotbarButton >= 0 && hotbarButton <= 8) {
                    swap(player, inventory, slot, hotbarButton);
                }
            }
            case SWAP_OFFHAND -> swap(player, inventory, slot, OFFHAND_SLOT);
            case DROP -> drop(player, inventory, slot, false);
            case CONTROL_DROP -> drop(player, inventory, slot, true);
            case MIDDLE -> cloneCreative(player, inventory, slot);
            case DOUBLE_CLICK -> collect(player, inventory, false, inventory.getItem(slot));
            default -> {}
        }
    }

    // Clicks in the player's inventory that affect the vault
    static void clickPlayer(Player player, Inventory inventory, boolean locked, int slot, ClickType click) {
        PlayerInventory playerInventory = player.getInventory();
        if (slot < 0 || slot >= 36) {
            return;
        }
        switch (click) {
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                if (!locked) {
                    moveToVault(player, inventory, slot);
                }
            }
            case DOUBLE_CLICK -> collect(player, inventory, locked, playerInventory.getItem(slot));
            default -> {}
        }
    }

    private static void pickup(Player player, Inventory inventory, int slot, boolean left) {
        ItemStack current = item(inventory, slot);
        ItemStack cursor = cursor(player);

        if (current == null && cursor == null) {
            return;
        }
        if (cursor == null) {
            int take = left ? current.getAmount() : (current.getAmount() + 1) / 2;
            player.setItemOnCursor(withAmount(current, take));
            inventory.setItem(slot, withAmount(current, current.getAmount() - take));
            return;
        }
        if (current == null) {
            int place = Math.min(left ? cursor.getAmount() : 1, maxStack(inventory, cursor));
            inventory.setItem(slot, withAmount(cursor, place));
            player.setItemOnCursor(withAmount(cursor, cursor.getAmount() - place));
            return;
        }
        if (current.isSimilar(cursor)) {
            int room = maxStack(inventory, current) - current.getAmount();
            int place = Math.min(left ? cursor.getAmount() : 1, room);
            if (place <= 0) {
                return;
            }
            inventory.setItem(slot, withAmount(current, current.getAmount() + place));
            player.setItemOnCursor(withAmount(cursor, cursor.getAmount() - place));
            return;
        }
        if (cursor.getAmount() <= maxStack(inventory, cursor)) {
            inventory.setItem(slot, cursor);
            player.setItemOnCursor(current);
        }
    }

    private static void moveToPlayer(Player player, Inventory inventory, int slot) {
        ItemStack current = item(inventory, slot);
        if (current == null) {
            return;
        }
        int remaining = insert(player.getInventory(), current, PLAYER_INSERT_ORDER);
        inventory.setItem(slot, withAmount(current, remaining));
    }

    private static void moveToVault(Player player, Inventory inventory, int playerSlot) {
        PlayerInventory playerInventory = player.getInventory();
        ItemStack current = item(playerInventory, playerSlot);
        if (current == null || isPlaceholder(current)) {
            return;
        }
        int[] order = range(0, inventory.getSize() - 1);
        int remaining = insert(inventory, current, order);
        playerInventory.setItem(playerSlot, withAmount(current, remaining));
    }

    // Merges into similar stacks first, then fills empty slots and returns what didn't fit
    private static int insert(Inventory target, ItemStack stack, int[] order) {
        int remaining = stack.getAmount();
        if (stack.getMaxStackSize() > 1) {
            for (int i : order) {
                if (remaining <= 0) break;
                ItemStack existing = item(target, i);
                if (existing == null || !existing.isSimilar(stack) || isPlaceholder(existing)) continue;
                int move = Math.min(remaining, maxStack(target, existing) - existing.getAmount());
                if (move <= 0) continue;
                target.setItem(i, withAmount(existing, existing.getAmount() + move));
                remaining -= move;
            }
        }
        for (int i : order) {
            if (remaining <= 0) break;
            if (item(target, i) != null) continue;
            int move = Math.min(remaining, maxStack(target, stack));
            target.setItem(i, withAmount(stack, move));
            remaining -= move;
        }
        return remaining;
    }

    private static void swap(Player player, Inventory inventory, int slot, int playerSlot) {
        PlayerInventory playerInventory = player.getInventory();
        ItemStack current = item(inventory, slot);
        ItemStack other = item(playerInventory, playerSlot);
        if (current == null && other == null) {
            return;
        }
        if (other == null) {
            playerInventory.setItem(playerSlot, current);
            inventory.setItem(slot, null);
            return;
        }
        if (isPlaceholder(other)) {
            return;
        }
        int max = maxStack(inventory, other);
        if (current == null) {
            int move = Math.min(other.getAmount(), max);
            inventory.setItem(slot, withAmount(other, move));
            playerInventory.setItem(playerSlot, withAmount(other, other.getAmount() - move));
            return;
        }
        if (other.getAmount() > max) {
            return;
        }
        inventory.setItem(slot, other);
        playerInventory.setItem(playerSlot, current);
    }

    private static void drop(Player player, Inventory inventory, int slot, boolean all) {
        ItemStack current = item(inventory, slot);
        if (current == null || cursor(player) != null) {
            return;
        }
        int amount = all ? current.getAmount() : 1;
        inventory.setItem(slot, withAmount(current, current.getAmount() - amount));

        Location location = player.getEyeLocation().subtract(0, 0.3, 0);
        Item dropped = player.getWorld().dropItem(location, withAmount(current, amount), item -> {
            item.setVelocity(location.getDirection().multiply(0.3));
            item.setPickupDelay(40);
            item.setThrower(player.getUniqueId());
        });
        if (!dropped.isValid()) {
            // The drop was prevented -> keep the item in the vault
            inventory.setItem(slot, current);
        }
    }

    private static void cloneCreative(Player player, Inventory inventory, int slot) {
        ItemStack current = item(inventory, slot);
        if (current != null && cursor(player) == null && player.getGameMode() == GameMode.CREATIVE) {
            player.setItemOnCursor(withAmount(current, current.getMaxStackSize()));
        }
    }

    // Double-clicking gathers matching items from both inventories onto the cursor
    private static void collect(Player player, Inventory inventory, boolean locked, @Nullable ItemStack clicked) {
        ItemStack cursor = cursor(player);
        if (cursor == null || (clicked != null && !clicked.isEmpty()) || isPlaceholder(cursor)) {
            return;
        }
        PlayerInventory playerInventory = player.getInventory();
        boolean vaultAllowed = !locked;
        int max = cursor.getMaxStackSize();
        int amount = cursor.getAmount();

        for (int pass = 0; pass < 2 && amount < max; pass++) {
            if (vaultAllowed) {
                for (int i = 0; i < inventory.getSize() && amount < max; i++) {
                    amount = take(inventory, i, cursor, amount, max, pass);
                }
            }
            for (int i : PLAYER_MENU_ORDER) {
                if (amount >= max) break;
                amount = take(playerInventory, i, cursor, amount, max, pass);
            }
        }
        player.setItemOnCursor(withAmount(cursor, amount));
    }

    private static int take(Inventory inventory, int slot, ItemStack cursor, int amount, int max, int pass) {
        ItemStack existing = item(inventory, slot);
        if (existing == null || !existing.isSimilar(cursor) || isPlaceholder(existing)) {
            return amount;
        }
        // Only take full stacks on the second pass:
        if (pass == 0 && existing.getAmount() >= existing.getMaxStackSize()) {
            return amount;
        }
        int move = Math.min(existing.getAmount(), max - amount);
        inventory.setItem(slot, withAmount(existing, existing.getAmount() - move));
        return amount + move;
    }

    record DragTarget(boolean vault, int slot) {
    }

    static void drag(Player player, Inventory inventory, boolean locked, List<DragTarget> targets, DragType type) {
        ItemStack cursor = cursor(player);
        if (cursor == null || isPlaceholder(cursor)) {
            return;
        }
        PlayerInventory playerInventory = player.getInventory();

        List<DragTarget> eligible = new ArrayList<>();
        for (DragTarget target : targets) {
            Inventory into = target.vault() ? inventory : playerInventory;
            int limit = target.vault() ? into.getSize() : 36;
            if (target.slot() < 0 || target.slot() >= limit) continue;
            ItemStack existing = item(into, target.slot());
            if (target.vault() && (locked || isPlaceholder(existing))) continue;
            if (existing == null || (existing.isSimilar(cursor) && existing.getAmount() < maxStack(into, existing))) {
                eligible.add(target);
            }
        }
        if (eligible.isEmpty()) {
            return;
        }

        int remaining = cursor.getAmount();
        int perSlot = type == DragType.SINGLE ? 1 : Math.max(1, remaining / eligible.size());
        for (DragTarget target : eligible) {
            if (remaining <= 0) break;
            Inventory into = target.vault() ? inventory : playerInventory;
            ItemStack existing = item(into, target.slot());
            int has = existing == null ? 0 : existing.getAmount();
            int move = Math.min(Math.min(perSlot, remaining), maxStack(into, cursor) - has);
            if (move <= 0) continue;
            into.setItem(target.slot(), withAmount(cursor, has + move));
            remaining -= move;
        }
        player.setItemOnCursor(withAmount(cursor, remaining));
    }

    private static boolean isPlaceholder(@Nullable ItemStack item) {
        return item != null && placeholderCheck.test(item);
    }

    @Nullable
    private static ItemStack cursor(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        return cursor.isEmpty() ? null : cursor.clone();
    }

    @Nullable
    private static ItemStack item(Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        return item == null || item.isEmpty() ? null : item.clone();
    }

    @Nullable
    private static ItemStack withAmount(ItemStack item, int amount) {
        if (amount <= 0) {
            return null;
        }
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }

    private static int maxStack(Inventory inventory, ItemStack item) {
        return Math.min(inventory.getMaxStackSize(), item.getMaxStackSize());
    }

    private static int[] range(int from, int to) {
        int step = from <= to ? 1 : -1;
        int[] result = new int[Math.abs(to - from) + 1];
        for (int i = 0; i < result.length; i++) {
            result[i] = from + i * step;
        }
        return result;
    }

    private static int[] order(int[] first, int[] second) {
        int[] result = new int[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
