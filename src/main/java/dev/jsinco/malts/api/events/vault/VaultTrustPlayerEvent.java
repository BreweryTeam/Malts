package dev.jsinco.malts.api.events.vault;

import dev.jsinco.malts.api.events.interfaces.EventAction;
import dev.jsinco.malts.api.events.interfaces.VaultEvent;
import dev.jsinco.malts.model.Vault;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called when a player is trusted or untrusted on a vault.
 * If the player cannot add the player to their trust list (because trust list is at cap),
 * this event will be called in a cancelled state
 */
public class VaultTrustPlayerEvent extends VaultEvent {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    public final EventAction action;
    private UUID trustedUUID;

    private boolean cancelled;

    public VaultTrustPlayerEvent(@NotNull Vault vault, @NotNull EventAction action, @NotNull UUID trustedUUID, boolean async) {
        super(vault, async);
        this.action = action;
        this.trustedUUID = trustedUUID;
    }

    /**
     * The action being performed, either TRUST or UNTRUST
     * @return Action being performed
     */
    @NotNull
    public EventAction getAction() {
        return action;
    }

    /**
     * The UUID of the player being trusted or untrusted
     * @return UUID of the player being trusted or untrusted
     */
    @NotNull
    public UUID getTrustedUUID() {
        return trustedUUID;
    }

    /**
     * Sets the UUID of the player being trusted or untrusted
     * @param trustedUUID New UUID of the player being trusted or untrusted
     */
    public void setTrustedUUID(@NotNull UUID trustedUUID) {
        this.trustedUUID = trustedUUID;
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
