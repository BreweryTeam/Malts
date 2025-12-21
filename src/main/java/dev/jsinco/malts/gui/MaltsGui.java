package dev.jsinco.malts.gui;

import dev.jsinco.malts.api.events.gui.MaltsGuiOpenEvent;
import dev.jsinco.malts.model.MaltsInventory;
import dev.jsinco.malts.gui.item.AbstractGuiItem;
import dev.jsinco.malts.gui.item.IgnoreAutoRegister;
import dev.jsinco.malts.utility.Executors;
import dev.jsinco.malts.utility.Text;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a GUI inventory in Malts.
 * GUIs are special inventories that are not the same as vaults.
 * Primarily, they are used for menus and other interactive interfaces.
 */
@Getter
public abstract class MaltsGui implements MaltsInventory {

    protected final Inventory inventory;
    protected final List<AbstractGuiItem> guiItems;


    public MaltsGui(String title, int size) {
        this.inventory = Bukkit.createInventory(this, size, Text.mm(title));
        this.guiItems = new ArrayList<>();
    }

    protected abstract void onInventoryClick(InventoryClickEvent event);
    public abstract void openImpl(Player player);


    public void open(Player player) {
        Executors.runSync(() -> {
            MaltsGuiOpenEvent event = new MaltsGuiOpenEvent(this, player, false);
            if (event.callEvent()) {
                this.openImpl(event.getPlayer());
            }
        });
    }


    public void addGuiItem(AbstractGuiItem item) {
        int index = item.index() != null ? item.index() : -1;
        addGuiItem(item, index);
    }

    public void addGuiItem(AbstractGuiItem item, int index) {
        if (index >= 0 && index < inventory.getSize()) {
            inventory.setItem(index, item.getItemStack());
        }
        guiItems.add(item);
    }

    public void onPreInventoryClick(InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem != null) {
            for (AbstractGuiItem guiItem : guiItems) {
                guiItem.onClick(event, clickedItem);
            }
        }

        // Let children handle the rest
        this.onInventoryClick(event);
    }

    protected void autoRegister(Class<?> forClass) {
        for (Field field : forClass.getDeclaredFields()) {
            field.setAccessible(true);
            if (!AbstractGuiItem.class.isAssignableFrom(field.getType()) || field.isAnnotationPresent(IgnoreAutoRegister.class)) continue;

            try {
                AbstractGuiItem guiItem = (AbstractGuiItem) field.get(this);
                if (guiItem != null) {
                    addGuiItem(guiItem);
                } else {
                    Text.error("GuiItem field '" + field.getName() + "' is null in " + forClass.getSimpleName());
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected void autoRegister(boolean walk) {
        Class<?> currentClass = this.getClass();
        if (walk) {
            while (currentClass != null && currentClass != Object.class) {
                this.autoRegister(currentClass);
                currentClass = currentClass.getSuperclass();
            }
        } else {
            this.autoRegister(currentClass);
        }
    }

}
