package dev.jsinco.malts.api.events.vault;

import dev.jsinco.malts.api.events.interfaces.VaultEvent;
import dev.jsinco.malts.model.Vault;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Called when a player attempts to open a vault.
 * If the player cannot open the vault (because another player that does not have the permission 'malts.mod' is viewing the vault already),
 * the event will be called in a cancelled state.
 */
public class VaultOpenEvent extends VaultEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;
    private final List<Player> currentViewers;

    private boolean cancelled;

    public VaultOpenEvent(@NotNull Vault vault, @NotNull Player viewer, List<Player> currentViewers, boolean async) {
        super(vault, async);
        this.player = viewer;
        this.currentViewers = List.copyOf(currentViewers);
    }

    /**
     * The player attempting to open the vault
     * @return Player attempting to open the vault
     */
    @NotNull
    public Player getPlayer() {
        return player;
    }


    /**
     * The players currently viewing the vault
     * @return List of players currently viewing the vault
     */
    @NotNull
    public List<Player> getCurrentViewers() {
        return currentViewers;
    }


    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }


    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

}
