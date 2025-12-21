package dev.jsinco.malts.api.events.warehouse;

import dev.jsinco.malts.api.events.interfaces.EventAction;
import dev.jsinco.malts.api.events.interfaces.WarehouseEvent;
import dev.jsinco.malts.model.Warehouse;
import org.bukkit.Material;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;


public class WarehouseCompartmentEvent extends WarehouseEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final EventAction action;


    public WarehouseCompartmentEvent(Warehouse warehouse, EventAction action, Material material, boolean async) {
        super(warehouse, material, async);
        this.action = action;
    }

    /**
     * The action being performed, either ADD or REMOVE
     * @return Action being performed
     */
    @NotNull
    public EventAction getAction() {
        return action;
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
