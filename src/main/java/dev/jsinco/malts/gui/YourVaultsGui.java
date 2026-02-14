package dev.jsinco.malts.gui;

import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.files.GuiConfig;
import dev.jsinco.malts.configuration.IntPair;
import dev.jsinco.malts.configuration.files.Lang;
import dev.jsinco.malts.gui.item.GuiItem;
import dev.jsinco.malts.obj.MaltsPlayer;
import dev.jsinco.malts.obj.SnapshotVault;
import dev.jsinco.malts.obj.Warehouse;
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

    private static final GuiConfig cfg = ConfigManager.get(GuiConfig.class);
    private static final Lang lng = ConfigManager.get(Lang.class);

    private PaginatedGui paginatedGui;
    private MaltsPlayer maltsPlayer;
    private final Inventory secondInv;
    private final boolean withQuickbar;

    private final GuiItem warehouseButton = GuiItem.builder()
            .itemStack(b -> b
                    .displayName(cfg.yourVaultsGui().warehouseQuickbar().name())
                    .material(cfg.yourVaultsGui().warehouseQuickbar().material())
                    .lore(cfg.yourVaultsGui().warehouseQuickbar().lore())
            )
            .action(e -> {
                Warehouse warehouse = DataSource.getInstance().cachedObject(maltsPlayer.getUuid(), Warehouse.class);
                WarehouseGui warehouseGui = new WarehouseGui(warehouse, maltsPlayer);
                warehouseGui.open((Player) e.getWhoClicked());
            })
            .build();
    private final GuiItem previousPage = GuiItem.builder()
            .itemStack(b -> b
                    .displayName(cfg.yourVaultsGui().previousPage().name())
                    .material(cfg.yourVaultsGui().previousPage().material())
                    .lore(cfg.yourVaultsGui().previousPage().lore())
            )
            .action(e -> {
                Player player = (Player) e.getWhoClicked();

                Inventory inv = paginatedGui.getPrevious(e.getInventory());
                if (inv != null) {
                    player.openInventory(inv);
                } else {
                    lng.entry(l -> l.gui().firstPage(), player);
                }
            })
            .build();
    private final GuiItem nextPage = GuiItem.builder()
            .itemStack(b -> b
                    .displayName(cfg.yourVaultsGui().nextPage().name())
                    .material(cfg.yourVaultsGui().nextPage().material())
                    .lore(cfg.yourVaultsGui().nextPage().lore())
            )
            .action(e -> {
                Player player = (Player) e.getWhoClicked();

                Inventory inv = paginatedGui.getNext(e.getInventory());
                if (inv != null) {
                    player.openInventory(inv);
                } else {
                    lng.entry(l -> l.gui().lastPage(), player);
                }
            })
            .build();


    public YourVaultsGui(MaltsPlayer maltsPlayer) {
        super(cfg.yourVaultsGui().title(), cfg.yourVaultsGui().size());
        this.maltsPlayer = maltsPlayer;
        this.secondInv = Bukkit.createInventory(this, 54, Text.mm(cfg.yourVaultsGui().title()));
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
            IntPair slots = withQuickbar ? cfg.yourVaultsGui().vaultItem().slots() : cfg.yourVaultsGui().vaultItem().altSlots();
            List<Integer> ignoredSlots = withQuickbar ? cfg.yourVaultsGui().vaultItem().ignoredSlots() : cfg.yourVaultsGui().vaultItem().altIgnoredSlots();

            for (int i = 0; i < inventory.getSize() && !itemStacks.isEmpty(); i++) {
                if (slots.includes(i) && !ignoredSlots.contains(i) && !itemStacks.isEmpty()) {
                    ItemStack itemStack = itemStacks.removeFirst();
                    inventory.setItem(i, itemStack);
                }
            }


            IntPair paginatedSlots = cfg.yourVaultsGui().vaultItem().altSlots();
            this.paginatedGui = PaginatedGui.builder()
                    .name(cfg.yourVaultsGui().title())
                    .items(itemStacks)
                    .startEndSlots(paginatedSlots.a(), paginatedSlots.b())
                    .ignoredSlots(cfg.yourVaultsGui().vaultItem().altIgnoredSlots())
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
            Executors.sync(player, () -> player.openInventory(inventory));
        });
    }


    private boolean assemble(Inventory inv, @Nullable Warehouse warehouse) {
        boolean quickBar = assembleQuickbar(inv, warehouse);

        int previousPageSlot = quickBar ? cfg.yourVaultsGui().previousPage().slot() : cfg.yourVaultsGui().previousPage().altSlot();
        int nextPageSlot = quickBar ? cfg.yourVaultsGui().nextPage().slot() : cfg.yourVaultsGui().nextPage().altSlot();
        inv.setItem(previousPageSlot, previousPage.getItemStack());
        inv.setItem(nextPageSlot, nextPage.getItemStack());

        IntPair slots = quickBar ? cfg.yourVaultsGui().vaultItem().slots() : cfg.yourVaultsGui().vaultItem().altSlots();
        IntPair warehouseSlots = quickBar ? cfg.yourVaultsGui().warehouseQuickbar().slots() : null;
        List<Integer> ignoredSlots = quickBar ? cfg.yourVaultsGui().vaultItem().ignoredSlots() : cfg.yourVaultsGui().vaultItem().altIgnoredSlots();
        List<Integer> ignoredWarehouseSlots = quickBar ? cfg.yourVaultsGui().warehouseQuickbar().ignoredSlots() : null;

        if (cfg.yourVaultsGui().borders()) {
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
        IntPair slots = cfg.yourVaultsGui().warehouseQuickbar().slots();
        int warehouseButtonSlot = cfg.yourVaultsGui().warehouseQuickbar().slot();

        if (warehouse == null || slots.negative() || warehouseButtonSlot < 0) {
            return false;
        }

        int amount = slots.difference(false) + 1;
        List<GuiItem> warehouseItems = warehouse.stockAsGuiItems(amount);
        if (warehouseItems.isEmpty()) {
            return false;
        }

        inv.setItem(warehouseButtonSlot, warehouseButton.getItemStack());

        List<Integer> ignoredSlots = cfg.yourVaultsGui().warehouseQuickbar().ignoredSlots();
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
