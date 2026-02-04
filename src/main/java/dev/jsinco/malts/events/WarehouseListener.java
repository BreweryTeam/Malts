package dev.jsinco.malts.events;

import dev.jsinco.malts.obj.MaltsPlayer;
import dev.jsinco.malts.storage.DataSource;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;

public class WarehouseListener implements Listener {

    private void handle(Event event, Player player, boolean expectCached) {
        DataSource dataSource = DataSource.getInstance();
        MaltsPlayer maltsPlayer = dataSource.cachedObject(player.getUniqueId(), MaltsPlayer.class);
        if (maltsPlayer == null) {
            if (!expectCached) {
                dataSource.cacheObject(dataSource.getMaltsPlayer(player.getUniqueId())).thenAccept(cached -> {
                    // Not sure how they weren't cached, but future events should be fine now.
                });
                throw new IllegalStateException("MaltsPlayer is not cached. UUID: " + player.getUniqueId());
            }
            return;
        }
        maltsPlayer.getWarehouseMode().handle(event, maltsPlayer, player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerAttemptPickupItem(PlayerAttemptPickupItemEvent event) {
        // This event can fire after `PlayerQuitEvent`, so we fail silently here.
        handle(event, event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        handle(event, event.getPlayer(), false);
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        handle(event, event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        handle(event, event.getPlayer(), false);
    }

}
