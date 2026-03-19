package dev.jsinco.malts.gui.item;

import dev.jsinco.malts.Malts;
import dev.jsinco.malts.utility.Util;
import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractGuiItem {

    private ItemStack cachedItemStack;

    protected abstract ItemStack itemStack();

    public abstract void onClick(InventoryClickEvent event, ItemStack clickedItem);

    public @Nullable Integer index() {
        return null;
    }


    public final NamespacedKey key() {
        String name = this.getClass().getSimpleName() + "_" + System.identityHashCode(this);
        return Util.namespacedKey(name);
    }

    public final ItemStack getItemStack(boolean cached) {
        boolean shouldRebuild = !cached || cachedItemStack == null || Malts.isInvalidatedCachedGuiItems();

        if (shouldRebuild) {
            ItemStack itemStack = Util.setPersistentKey(itemStack(), key(), PersistentDataType.BOOLEAN, true);

            if (!cached) {
                return itemStack;
            }

            this.cachedItemStack = itemStack;
            Malts.setInvalidatedCachedGuiItems(false);
        }

        return this.cachedItemStack;
    }

    public final ItemStack getItemStack() {
        return getItemStack(true);
    }

}
