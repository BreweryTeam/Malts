package dev.jsinco.malts.events;

import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.files.Lang;
import dev.jsinco.malts.model.Vault;
import dev.jsinco.malts.model.VaultKey;
import dev.jsinco.malts.utility.BorrowedItems;
import dev.jsinco.malts.utility.Executors;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Handles borrowing from locked vaults, returning borrowed items and keeping placeholders inside their vault
public class VaultBorrowListener implements Listener {

    private static final Lang LANG = ConfigManager.get(Lang.class);
    private static final String ADMIN_PERMISSION = "malts.admin";
    private static final long REMOVE_CONFIRM_MILLIS = 1500;
    private static final int OFFHAND_SLOT = 40;

    private final Map<UUID, PendingRemoval> pendingRemovals = new ConcurrentHashMap<>();

    private record PendingRemoval(VaultKey vault, int slot, long time) {
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onVaultClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Vault vault)) {
            return;
        }
        Inventory top = event.getInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        if (clicked != top) {
            switch (event.getAction()) {
                case MOVE_TO_OTHER_INVENTORY -> handleShiftReturn(event, vault, top);
                case COLLECT_TO_CURSOR -> event.setCancelled(vault.isLockActive());
                default -> {}
            }
        } else if (BorrowedItems.isPlaceholder(event.getCurrentItem())) {
            event.setCancelled(true);
            handlePlaceholderClick(event, player, vault, top);
        } else if (vault.isLockActive()) {
            event.setCancelled(true);
            handleBorrow(event, player, top);
        }

        if (event.isCancelled()) {
            Executors.delayedSync(player, 1, player::updateInventory);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onVaultDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Vault vault)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize && (vault.isLockActive() || BorrowedItems.isPlaceholder(event.getView().getItem(rawSlot)))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // Before the log listener snapshots contents so refreshed lore isn't logged as a change
    @EventHandler(priority = EventPriority.LOWEST)
    public void onVaultOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder(false) instanceof Vault) {
            BorrowedItems.refreshLore(event.getInventory());
        } else {
            purge(event.getInventory());
        }
        purge(event.getPlayer().getInventory());
    }

    private void handleBorrow(InventoryClickEvent event, Player player, Inventory top) {
        ItemStack current = event.getCurrentItem();
        if (current == null || current.isEmpty()) {
            return;
        }

        PlayerInventory playerInventory = player.getInventory();
        int hotbarSlot = hotbarSlot(event);
        if (hotbarSlot >= 0) {
            if (!isEmpty(playerInventory.getItem(hotbarSlot))) return;
        } else if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (playerInventory.firstEmpty() == -1) return;
        } else if (!isPickup(event) || !isEmpty(player.getItemOnCursor())) {
            return;
        }

        ItemStack real = current.clone();
        top.setItem(event.getSlot(), BorrowedItems.borrow(real, player));

        if (hotbarSlot >= 0) {
            playerInventory.setItem(hotbarSlot, real);
        } else if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            playerInventory.addItem(real);
        } else {
            player.setItemOnCursor(real);
        }
    }

    private void handlePlaceholderClick(InventoryClickEvent event, Player player, Vault vault, Inventory top) {
        ItemStack placeholder = event.getCurrentItem();
        if (placeholder == null) return;
        int slot = event.getSlot();
        int hotbarSlot = hotbarSlot(event);

        if (hotbarSlot >= 0) {
            ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
            if (BorrowedItems.canReturn(hotbarItem, placeholder)) {
                top.setItem(slot, BorrowedItems.takeReturn(hotbarItem, placeholder));
                player.getInventory().setItem(hotbarSlot, hotbarItem);
            }
        } else if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
            if (!vault.isLockActive() && (vault.getOwner().equals(player.getUniqueId()) || player.hasPermission(ADMIN_PERMISSION))) {
                confirmRemoval(player, vault, top, slot);
            }
        } else if (event.getClick() != ClickType.MIDDLE && BorrowedItems.canReturn(player.getItemOnCursor(), placeholder)) {
            ItemStack cursor = player.getItemOnCursor();
            top.setItem(slot, BorrowedItems.takeReturn(cursor, placeholder));
            player.setItemOnCursor(cursor);
        }
    }

    // Shift-clicking a borrowed item returns it to its placeholder's slot(s) instead of the first free one
    private void handleShiftReturn(InventoryClickEvent event, Vault vault, Inventory top) {
        ItemStack item = event.getCurrentItem();
        boolean returned = false;
        for (int i = 0; i < top.getSize() && item != null && !item.isEmpty(); i++) {
            ItemStack placeholder = top.getItem(i);
            if (placeholder == null) continue;
            if (BorrowedItems.canReturn(item, placeholder)) {
                top.setItem(i, BorrowedItems.takeReturn(item, placeholder));
                returned = true;
            }
        }
        if (returned) {
            event.setCurrentItem(item);
        }
        if (returned || vault.isLockActive()) {
            event.setCancelled(true);
        }
    }

    private void confirmRemoval(Player player, Vault vault, Inventory top, int slot) {
        long now = System.currentTimeMillis();
        PendingRemoval pending = pendingRemovals.get(player.getUniqueId());
        if (pending != null && pending.slot() == slot && pending.vault().equals(vault.getKey()) && now - pending.time() <= REMOVE_CONFIRM_MILLIS) {
            pendingRemovals.remove(player.getUniqueId());
            top.setItem(slot, null);
            return;
        }
        pendingRemovals.put(player.getUniqueId(), new PendingRemoval(vault.getKey(), slot, now));
        LANG.actionBarEntry(l -> l.vaults().confirmRemoveBorrowed(), player);
    }

    private static int hotbarSlot(InventoryClickEvent event) {
        return switch (event.getClick()) {
            case NUMBER_KEY -> event.getHotbarButton();
            case SWAP_OFFHAND -> OFFHAND_SLOT;
            default -> -1;
        };
    }

    private static boolean isPickup(InventoryClickEvent event) {
        return switch (event.getAction()) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME -> true;
            default -> false;
        };
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.isEmpty();
    }


    // Placeholders should never leave their vault, so remove them wherever they show up anywhere else:

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClickOutsideVault(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (BorrowedItems.isPlaceholder(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            event.setCancelled(true);
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked != null && !(clicked.getHolder(false) instanceof Vault) && BorrowedItems.isPlaceholder(event.getCurrentItem())) {
            event.setCurrentItem(null);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        purge(event.getPlayer().getInventory());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        purge(event.getPlayer().getInventory());
        purge(event.getPlayer().getEnderChest());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingRemovals.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        PlayerInventory inventory = event.getPlayer().getInventory();
        if (BorrowedItems.isPlaceholder(inventory.getItem(event.getNewSlot()))) {
            inventory.setItem(event.getNewSlot(), null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (BorrowedItems.isPlaceholder(event.getItemDrop().getItemStack())) {
            event.getItemDrop().remove();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (BorrowedItems.isPlaceholder(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (BorrowedItems.isPlaceholder(event.getItem().getItemStack())) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (BorrowedItems.isPlaceholder(event.getItem().getItemStack())) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMoveItem(InventoryMoveItemEvent event) {
        if (BorrowedItems.isPlaceholder(event.getItem())) {
            event.setCancelled(true);
            event.getSource().removeItem(event.getItem());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (BorrowedItems.isPlaceholder(event.getItemInHand())) {
            event.setCancelled(true);
            purge(event.getPlayer().getInventory());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (BorrowedItems.isPlaceholder(event.getItem())) {
            event.setCancelled(true);
            purge(event.getPlayer().getInventory());
        }
    }

    private static void purge(Inventory inventory) {
        if (inventory.getHolder(false) instanceof Vault) {
            return;
        }
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (BorrowedItems.isPlaceholder(contents[i])) {
                inventory.setItem(i, null);
            }
        }
    }
}
