package dev.jsinco.malts.api.events.vault;

import dev.jsinco.malts.api.events.interfaces.VaultEvent;
import dev.jsinco.malts.model.Vault;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a player clicks in a vault inventory.
 * Wrapper for InventoryClickEvent.
 */
public class VaultClickEvent extends VaultEvent {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final InventoryClickEvent backing;

    public VaultClickEvent(@NotNull Vault vault, @NotNull InventoryClickEvent backing, boolean async) {
        super(vault, async);
        this.backing = backing;
    }

    /**
     * The underlying Bukkit InventoryClickEvent
     * @return Underlying Bukkit InventoryClickEvent
     */
    @NotNull
    public InventoryClickEvent getBacking() {
        return backing;
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
