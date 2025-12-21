package dev.jsinco.malts.api.events.vault;

import dev.jsinco.malts.api.events.interfaces.VaultEvent;
import dev.jsinco.malts.model.Vault;
import org.bukkit.Material;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a vault's icon is changed
 */
public class VaultIconChangeEvent extends VaultEvent {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private Material newIcon;

    public VaultIconChangeEvent(@NotNull Vault vault, @NotNull Material newIcon, boolean async) {
        super(vault, async);
        this.newIcon = newIcon;
    }

    /**
     * The new icon material for the vault
     * @return New icon material for the vault
     */
    @NotNull
    public Material getNewIcon() {
        return newIcon;
    }

    /**
     * Sets the new icon material for the vault
     * @param newIcon New icon material for the vault
     */
    public void setNewIcon(@NotNull Material newIcon) {
        this.newIcon = newIcon;
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
