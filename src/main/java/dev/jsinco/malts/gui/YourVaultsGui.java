package dev.jsinco.malts.gui;

import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.files.GuiConfig;
import dev.jsinco.malts.configuration.IntPair;
import dev.jsinco.malts.configuration.files.Lang;
import dev.jsinco.malts.gui.item.GuiItem;
import dev.jsinco.malts.model.MaltsPlayer;
import dev.jsinco.malts.model.SnapshotVault;
import dev.jsinco.malts.model.Warehouse;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.utility.Executors;
import dev.jsinco.malts.utility.ItemStacks;
import dev.jsinco.malts.utility.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class YourVaultsGui extends MaltsGui implements PromisedInventory {

    private static final GuiConfig GUI_CONFIG = ConfigManager.get(GuiConfig.class);
    private static final Lang LANG = ConfigManager.get(Lang.class);

    private PaginatedGui paginatedGui;
    private MaltsPlayer maltsPlayer;
    private final Inventory secondInv;
    private final boolean withQuickbar;

    private final GuiItem warehouseButton = GuiItem.builder()
            .itemStack(b -> b
                    .displayName(GUI_CONFIG.yourVaultsGui().warehouseQuickbar().name())
                    .material(GUI_CONFIG.yourVaultsGui().warehouseQuickbar().material())
                    .lore(GUI_CONFIG.yourVaultsGui().warehouseQuickbar().lore())
            )
            .action(e -> {
                Warehouse warehouse = DataSource.getInstance().cachedObject(maltsPlayer.getUuid(), Warehouse.class);
                WarehouseGui warehouseGui = new WarehouseGui(warehouse, maltsPlayer);
                warehouseGui.open((Player) e.getWhoClicked());
            })
            .build();
    private final GuiItem previousPage = GuiItem.builder()
            .itemStack(b -> b
                    .displayName(GUI_CONFIG.yourVaultsGui().previousPage().name())
                    .material(GUI_CONFIG.yourVaultsGui().previousPage().material())
                    .lore(GUI_CONFIG.yourVaultsGui().previousPage().lore())
            )
            .action(e -> {
                Player player = (Player) e.getWhoClicked();

                Inventory inv = paginatedGui.getPrevious(e.getInventory());
                if (inv != null) {
                    player.openInventory(inv);
                } else {
                    LANG.entry(l -> l.gui().firstPage(), player);
                }
            })
            .build();
    private final GuiItem nextPage = GuiItem.builder()
            .itemStack(b -> b
                    .displayName(GUI_CONFIG.yourVaultsGui().nextPage().name())
                    .material(GUI_CONFIG.yourVaultsGui().nextPage().material())
                    .lore(GUI_CONFIG.yourVaultsGui().nextPage().lore())
            )
            .action(e -> {
                Player player = (Player) e.getWhoClicked();

                Inventory inv = paginatedGui.getNext(e.getInventory());
                if (inv != null) {
                    player.openInventory(inv);
                } else {
                    LANG.entry(l -> l.gui().lastPage(), player);
                }
            })
            .build();


    public YourVaultsGui(MaltsPlayer maltsPlayer) {
        super(GUI_CONFIG.yourVaultsGui().title(), GUI_CONFIG.yourVaultsGui().size());
        this.maltsPlayer = maltsPlayer;
        this.secondInv = Bukkit.createInventory(this, 54, Text.mm(GUI_CONFIG.yourVaultsGui().title()));
        this.autoRegister(false);

        Warehouse warehouse = DataSource.getInstance().cachedObject(maltsPlayer.getUuid(), Warehouse.class);

        this.withQuickbar = this.assemble(this.inventory, warehouse);
        this.assemble(this.secondInv, null);
    }

    @Override
    public CompletableFuture<Inventory> promiseInventory() {
        DataSource dataSource = DataSource.getInstance();
        CompletableFuture<Inventory> future = new CompletableFuture<>();


        dataSource.getVaults(maltsPlayer.getUuid()).thenAccept(snapshotVaults -> {

            List<ItemStack> itemStacks = new ArrayList<>();

            for (int i = 0; i <  maltsPlayer.getCalculatedMaxVaults(); i++) {
                final int finalI = i;
                SnapshotVault snapshotVault = snapshotVaults.stream().filter(it -> it.getId() == finalI + 1).findFirst().orElse(null);
                if (snapshotVault == null) {
                    snapshotVault = new SnapshotVault(maltsPlayer.getUuid(), i + 1, null, null);
                }

                addGuiItem(snapshotVault);
                itemStacks.add(snapshotVault.getItemStack());
            }
            IntPair slots = withQuickbar ? GUI_CONFIG.yourVaultsGui().vaultItem().slots() : GUI_CONFIG.yourVaultsGui().vaultItem().altSlots();
            List<Integer> ignoredSlots = withQuickbar ? GUI_CONFIG.yourVaultsGui().vaultItem().ignoredSlots() : GUI_CONFIG.yourVaultsGui().vaultItem().altIgnoredSlots();

            for (int i = 0; i < inventory.getSize() && !itemStacks.isEmpty(); i++) {
                if (slots.includes(i) && !ignoredSlots.contains(i) && !itemStacks.isEmpty()) {
                    ItemStack itemStack = itemStacks.removeFirst();
                    inventory.setItem(i, itemStack);
                }
            }


            IntPair paginatedSlots = GUI_CONFIG.yourVaultsGui().vaultItem().altSlots();
            this.paginatedGui = PaginatedGui.builder()
                    .name(GUI_CONFIG.yourVaultsGui().title())
                    .items(itemStacks)
                    .startEndSlots(paginatedSlots.a(), paginatedSlots.b())
                    .ignoredSlots(GUI_CONFIG.yourVaultsGui().vaultItem().altIgnoredSlots())
                    .base(this.secondInv)
                    .buildIfEmpty(false)
                    .build();
            this.paginatedGui.insert(this.inventory, 0);


            future.complete(this.paginatedGui.getPage(0));
        });


        return future;
    }


    @Override
    public void onInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void openImpl(Player player) {
        promiseInventory().thenAccept(inventory -> {
            Executors.sync(() -> player.openInventory(inventory));
        });
    }


    private boolean assemble(Inventory inv, @Nullable Warehouse warehouse) {
        boolean quickBar = assembleQuickbar(inv, warehouse);

        int previousPageSlot = quickBar ? GUI_CONFIG.yourVaultsGui().previousPage().slot() : GUI_CONFIG.yourVaultsGui().previousPage().altSlot();
        int nextPageSlot = quickBar ? GUI_CONFIG.yourVaultsGui().nextPage().slot() : GUI_CONFIG.yourVaultsGui().nextPage().altSlot();
        inv.setItem(previousPageSlot, previousPage.getItemStack());
        inv.setItem(nextPageSlot, nextPage.getItemStack());

        IntPair slots = quickBar ? GUI_CONFIG.yourVaultsGui().vaultItem().slots() : GUI_CONFIG.yourVaultsGui().vaultItem().altSlots();
        IntPair warehouseSlots = quickBar ? GUI_CONFIG.yourVaultsGui().warehouseQuickbar().slots() : null;
        List<Integer> ignoredSlots = quickBar ? GUI_CONFIG.yourVaultsGui().vaultItem().ignoredSlots() : GUI_CONFIG.yourVaultsGui().vaultItem().altIgnoredSlots();
        List<Integer> ignoredWarehouseSlots = quickBar ? GUI_CONFIG.yourVaultsGui().warehouseQuickbar().ignoredSlots() : null;

        if (GUI_CONFIG.yourVaultsGui().borders()) {
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack itemStack = inv.getItem(i);
                if (itemStack != null || (slots.includes(i) && !ignoredSlots.contains(i))) continue;
                else if (quickBar && (warehouseSlots.includes(i) || ignoredWarehouseSlots.contains(i))) continue;

                inv.setItem(i, ItemStacks.borderItem());
            }
        }
        return quickBar;
    }

    private boolean assembleQuickbar(Inventory inv, @Nullable Warehouse warehouse) {
        IntPair slots = GUI_CONFIG.yourVaultsGui().warehouseQuickbar().slots();
        int warehouseButtonSlot = GUI_CONFIG.yourVaultsGui().warehouseQuickbar().slot();

        if (warehouse == null || slots.negative() || warehouseButtonSlot < 0) {
            return false;
        }

        int amount = slots.difference(false) + 1;
        List<GuiItem> warehouseItems = warehouse.stockAsGuiItems(amount);
        if (warehouseItems.isEmpty()) {
            return false;
        }

        inv.setItem(warehouseButtonSlot, warehouseButton.getItemStack());

        List<Integer> ignoredSlots = GUI_CONFIG.yourVaultsGui().warehouseQuickbar().ignoredSlots();
        for (int i = 0; i < Math.min(amount, warehouseItems.size()); i++) {
            if (ignoredSlots.contains(i)) {
                continue;
            }
            GuiItem item = warehouseItems.get(i);
            inv.setItem(slots.a() + i, item.getItemStack());
            this.addGuiItem(item);
        }
        return true;
    }
}
