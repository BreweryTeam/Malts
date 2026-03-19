package dev.jsinco.malts.api.events;

import dev.jsinco.malts.importers.Importer;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class ImportEvent extends MaltsEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private Importer importer;

    private boolean cancelled;

    public ImportEvent(Importer importer, boolean async) {
        super(async);
        this.importer = importer;
    }

    /**
     * The importer being used for the import
     * @return Importer being used for the import
     */
    @Nullable
    public Importer getImporter() {
        return importer;
    }

    /**
     * Sets the importer being used for the import
     * @param importer Importer being used for the import
     */
    public void setImporter(@Nullable Importer importer) {
        this.importer = importer;
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
