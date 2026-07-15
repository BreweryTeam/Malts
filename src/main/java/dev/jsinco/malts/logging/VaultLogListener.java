package dev.jsinco.malts.logging;

import dev.jsinco.malts.model.Vault;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VaultLogListener implements Listener {

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
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }
}
