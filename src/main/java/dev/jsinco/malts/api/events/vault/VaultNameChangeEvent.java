package dev.jsinco.malts.api.events.vault;

import dev.jsinco.malts.api.events.interfaces.VaultEvent;
import dev.jsinco.malts.model.Vault;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a vault's name is changed
 */
public class VaultNameChangeEvent extends VaultEvent {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private String newName;

    public VaultNameChangeEvent(@NotNull Vault vault, @NotNull String newName, boolean async) {
        super(vault, async);
        this.newName = newName;
    }

    /**
     * The new name for the vault
     * @return New name for the vault
     */
    @NotNull
    public String getNewName() {
        return newName;
    }

    /**
     * Sets the new name for the vault
     * @param newName New name for the vault
     */
    public void setNewName(@NotNull String newName) {
        this.newName = newName;
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
