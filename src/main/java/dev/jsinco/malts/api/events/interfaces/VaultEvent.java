package dev.jsinco.malts.api.events.interfaces;

import dev.jsinco.malts.api.events.MaltsEvent;
import dev.jsinco.malts.model.Vault;
import org.bukkit.event.Cancellable;
import org.jetbrains.annotations.NotNull;

/**
 * Base event for all vault-related events
 */
public abstract class VaultEvent extends MaltsEvent implements Cancellable {

    private final Vault vault;

    private boolean cancelled;

    public VaultEvent(@NotNull Vault vault, boolean async) {
        super(async);
        this.vault = vault;
    }

    /**
     * The vault involved in the event
     * @return Vault involved in the event
     */
    @NotNull
    public Vault getVault() {
        return vault;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
