package dev.jsinco.malts.integration;

import dev.jsinco.malts.registry.RegistryItem;

public interface Integration extends RegistryItem {

    boolean canRegister();

    void register();

    default void unregister() {
        // Optional unregister method
    }


    /**
     * An integration compiled into Malts
     */
    interface Compiled extends Integration {
        @Override
        default boolean canRegister() {
            return true;
        }
    }
}
