package dev.jsinco.malts.api.events.warehouse;

import dev.jsinco.malts.api.events.interfaces.WarehouseEvent;
import dev.jsinco.malts.model.Warehouse;
import org.bukkit.Material;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when material is stocked from a warehouse
 */
@SuppressWarnings({"LombokSetterMayBeUsed", "LombokGetterMayBeUsed"})
public class WarehouseStockEvent extends WarehouseEvent {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private int amount;

    public WarehouseStockEvent(Warehouse warehouse, Material material, int amount, boolean async) {
        super(warehouse, material, async);
        this.amount = amount;
    }

    /**
     * The amount of the item being stocked or removed
     * @return Amount of the item being stocked or removed
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Sets the amount of the item being stocked or removed
     * @param amount New amount of the item being stocked or removed
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
