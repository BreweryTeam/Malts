package dev.jsinco.malts.api.events;

import dev.jsinco.malts.api.events.interfaces.EventAction;
import dev.jsinco.malts.model.CachedObject;
import dev.jsinco.malts.storage.DataSource;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a CachedObject is cached to or removed from a DataSource
 *
 * @deprecated Not implemented.
 */
@Deprecated
public class CachedObjectEvent extends MaltsEvent {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final DataSource dataSource;
    private final CachedObject object;
    private final EventAction action;

    public CachedObjectEvent(@NotNull DataSource dataSource, @NotNull CachedObject object, @NotNull EventAction action, boolean async) {
        super(async);
        this.dataSource = dataSource;
        this.object = object;
        this.action = action;
    }

    /**
     * The data source the object is being cached to or removed from
     * @return Data source the object is being cached to or removed from
     */
    @NotNull
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * The object being cached or removed
     * @return Object being cached or removed
     */
    @NotNull
    public CachedObject getObject() {
        return object;
    }

    /**
     * The action being performed, either CACHE or UNCACHE
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
