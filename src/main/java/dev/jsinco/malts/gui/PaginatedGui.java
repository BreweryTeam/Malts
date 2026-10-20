package dev.jsinco.malts.gui;

import dev.jsinco.malts.utility.Couple;
import dev.jsinco.malts.utility.Text;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Getter
public class PaginatedGui {

    private final String name;
    private final Inventory base;
    private final List<Inventory> pages = new ArrayList<>();


    public PaginatedGui(String name, Inventory base, List<ItemStack> items, Couple<Integer, Integer> startEndSlots, List<Integer> ignoredSlots, boolean buildIfEmpty) {
        this.name = name;
        this.base = base;
        if (items.isEmpty() && !buildIfEmpty) {
            return;
        }
        Inventory currentPage = newPage();
        int currentItem = 0;
        int currentSlot = startEndSlots.getFirst();

        while (currentItem < items.size()) {
            if (!ignoredSlots.contains(currentSlot) && currentPage.getItem(currentSlot) == null) {
                currentPage.setItem(currentSlot, items.get(currentItem++));
            }

            if (currentSlot >= startEndSlots.getSecond() && currentItem < items.size()) {
                currentPage = newPage();
                currentSlot = startEndSlots.getFirst();
            } else {
                currentSlot++;
            }
        }
    }

    private Inventory newPage() {
        Inventory inventory = Bukkit.createInventory(base.getHolder(), base.getSize(), Text.mm(name));
        inventory.setContents(base.getContents());
        pages.add(inventory);
        return inventory;
    }

    public Inventory getPage(int page) {
        return pages.get(page);
    }

    public Inventory getPageClamped(int page) {
        return pages.get(Math.max(0, Math.min(page, pages.size() - 1)));
    }

    public int indexOf(Inventory page) {
        return pages.indexOf(page);
    }

    @Nullable
    public Inventory getNext(Inventory page) {
        int index = pages.indexOf(page);
        return (index == -1 || index + 1 >= pages.size()) ? null : pages.get(index + 1);
    }

    @Nullable
    public Inventory getPrevious(Inventory page) {
        int index = pages.indexOf(page);
        return (index <= 0) ? null : pages.get(index - 1);
    }

    public void insert(Inventory page, int index) {
        InventoryHolder holder = page.getHolder();
        InventoryHolder baseHolder = base.getHolder();
        if (holder == null || !holder.equals(baseHolder)) {
            throw new IllegalArgumentException("Page and base inventory must be of the same type");
        }
        pages.add(index, page);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name = "Paginated GUI";
        private Inventory base;
        private List<ItemStack> items = Collections.emptyList();
        private Couple<Integer, Integer> startEndSlots = new Couple<>(0, 0);
        private List<Integer> ignoredSlots = Collections.emptyList();
        private boolean buildIfEmpty = true;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder base(Inventory base) {
            this.base = base;
            return this;
        }

        public Builder items(List<ItemStack> items) {
            this.items = items;
            return this;
        }

        public Builder startEndSlots(int start, int end) {
            this.startEndSlots = new Couple<>(start, end);
            return this;
        }

        public Builder ignoredSlots(Integer... ignoredSlots) {
            this.ignoredSlots = Arrays.asList(ignoredSlots);
            return this;
        }

        public Builder ignoredSlots(List<Integer> ignoredSlots) {
            this.ignoredSlots = ignoredSlots;
            return this;
        }

        public Builder buildIfEmpty(boolean buildIfEmpty) {
            this.buildIfEmpty = buildIfEmpty;
            return this;
        }

        public PaginatedGui build() {
            return new PaginatedGui(name, base, items, startEndSlots, ignoredSlots, buildIfEmpty);
        }
    }

}
