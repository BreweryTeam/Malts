package dev.jsinco.malts.api.events.warehouse;

import dev.jsinco.malts.api.events.interfaces.WarehouseEvent;
import dev.jsinco.malts.model.Warehouse;
import org.bukkit.Material;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when material is destocked from a warehouse
 */
@SuppressWarnings({"LombokSetterMayBeUsed", "LombokGetterMayBeUsed"})
public class WarehouseDestockEvent extends WarehouseEvent {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private int amount;

    public WarehouseDestockEvent(Warehouse warehouse, Material material, int amount, boolean async) {
        super(warehouse, material, async);
        this.amount = amount;
    }

    /**
     * The amount of the item being destocked
     * @return Amount of the item being destocked
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Sets the amount of the item being destocked
     * @param amount New amount of the item being destocked
     */
    public void setAmount(int amount) {
        this.amount = amount;
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
