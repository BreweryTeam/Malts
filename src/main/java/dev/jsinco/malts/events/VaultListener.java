package dev.jsinco.malts.events;

import dev.jsinco.malts.api.events.vault.VaultClickEvent;
import dev.jsinco.malts.configuration.files.Config;
import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.enums.QuickReturnClickType;
import dev.jsinco.malts.gui.MaltsGui;
import dev.jsinco.malts.gui.VaultOtherGui;
import dev.jsinco.malts.gui.YourVaultsGui;
import dev.jsinco.malts.model.MaltsPlayer;
import dev.jsinco.malts.model.VaultSession;
import dev.jsinco.malts.model.VaultSessions;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.model.Vault;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class VaultListener implements Listener {

    // Leaving a vault saves it (see VaultSession)
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Vault view)) {
            return;
        }
        VaultSession session = VaultSessions.get(view.getKey());
        if (session != null) {
            session.leave((Player) event.getPlayer(), view);
        }
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
}
