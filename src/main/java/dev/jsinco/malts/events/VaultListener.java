package dev.jsinco.malts.events;

import dev.jsinco.malts.api.events.vault.VaultClickEvent;
import dev.jsinco.malts.configuration.files.Config;
import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.enums.QuickReturnClickType;
import dev.jsinco.malts.gui.MaltsGui;
import dev.jsinco.malts.gui.VaultOtherGui;
import dev.jsinco.malts.gui.YourVaultsGui;
import dev.jsinco.malts.logging.MaltsLogger;
import dev.jsinco.malts.model.MaltsPlayer;
import dev.jsinco.malts.model.VaultKey;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.model.Vault;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VaultListener implements Listener {

    private final Map<UUID, ItemStack[]> openSnapshots = new ConcurrentHashMap<>();

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Vault)) {
            return;
        }
        MaltsLogger logger = MaltsLogger.get();
        if (logger == null || !logger.enabled()) {
            return;
        }
        openSnapshots.put(event.getPlayer().getUniqueId(), cloneContents(event.getInventory().getContents()));
    }

    // Save vault data when the inventory is closed
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder(false);
        if (!(holder instanceof Vault vault)) {
            return;
        }

        ItemStack[] before = openSnapshots.remove(event.getPlayer().getUniqueId());
        MaltsLogger logger = MaltsLogger.get();
        if (before != null && logger != null) {
            logger.logVaultChanges(event.getPlayer(), vault, before, event.getInventory().getContents());
        }

        vault.update((Player) event.getPlayer());
        VaultKey key = vault.getKey();

        DataSource dataSource = DataSource.getInstance();

        // Starting save, lock the vault to prevent opening while we save
        dataSource.lock(key);

        dataSource.saveVault(vault).thenRun(() -> {
            // Finished saving, we can release
            dataSource.releaseLock(key);
        });
    }

    @EventHandler
    public void onInventoryInteract(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Vault vault)) {
            return;
        }
        vault.update((Player) event.getWhoClicked());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Vault vault)) {
            return;
        }
        VaultClickEvent vaultClickEvent = new VaultClickEvent(vault, event, false);
        if (!vaultClickEvent.callEvent()) {
            event.setCancelled(true);
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Config.QuickReturn quickReturn = ConfigManager.get(Config.class).quickReturn();

        if (event.getClickedInventory() != null) {
            vault.update(player);
            return;
        }

        MaltsPlayer maltsPlayer = DataSource.getInstance().cachedObject(player.getUniqueId(), MaltsPlayer.class);
        QuickReturnClickType quickReturnClickType = maltsPlayer.getQuickReturnClickType();

        if (!quickReturn.enabled() || quickReturnClickType == null || quickReturnClickType.getBacking() != event.getClick()) {
            return;
        }


        MaltsGui gui;
        if (!vault.getOwner().equals(player.getUniqueId())) {
            gui = new VaultOtherGui(player, Bukkit.getOfflinePlayer(vault.getOwner()));
        } else {
            gui = new YourVaultsGui(maltsPlayer);
        }
        gui.open(player);
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }
}
