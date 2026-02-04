package dev.jsinco.malts.gui;

import dev.jsinco.malts.configuration.files.Config;
import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.files.GuiConfig;
import dev.jsinco.malts.configuration.IntPair;
import dev.jsinco.malts.configuration.files.Lang;
import dev.jsinco.malts.enums.QuickReturnClickType;
import dev.jsinco.malts.enums.TriState;
import dev.jsinco.malts.enums.WarehouseMode;
import dev.jsinco.malts.gui.item.GuiItem;
import dev.jsinco.malts.gui.item.ItemConfirmation;
import dev.jsinco.malts.gui.item.UncontainedGuiItem;
import dev.jsinco.malts.obj.MaltsPlayer;
import dev.jsinco.malts.obj.Warehouse;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.utility.Couple;
import dev.jsinco.malts.utility.Executors;
import dev.jsinco.malts.utility.ItemStacks;
import dev.jsinco.malts.utility.Util;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


public class WarehouseGui extends MaltsGui {

    private static final GuiConfig cfg = ConfigManager.get(GuiConfig.class);
    private static final Lang lng = ConfigManager.get(Lang.class);

    private PaginatedGui paginatedGui;

    private Warehouse warehouse;
    private MaltsPlayer maltsPlayer;

    private ClickType state;


    private final GuiItem previousPage = GuiItem.builder()
            .index(() -> cfg.warehouseGui().previousPage().slot())
            .itemStack(b -> b
                    .displayName(cfg.warehouseGui().previousPage().title())
                    .material(cfg.warehouseGui().previousPage().material())
                    .lore(cfg.warehouseGui().previousPage().lore())
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
            .index(() -> cfg.warehouseGui().nextPage().slot())
            .itemStack(b -> b
                    .displayName(cfg.warehouseGui().nextPage().title())
                    .material(cfg.warehouseGui().nextPage().material())
                    .lore(cfg.warehouseGui().nextPage().lore())
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
    @SuppressWarnings("unchecked")
    private final UncontainedGuiItem managerButton = UncontainedGuiItem.builder()
            .index(() -> cfg.warehouseGui().managerButton().slot())
            .itemStack(b -> b
                    .stringReplacements(
                            Couple.of("{mode}", Util.formatEnumerator(maltsPlayer.getWarehouseMode()))
                    )
                    .displayName(cfg.warehouseGui().managerButton().name())
                    .material(cfg.warehouseGui().managerButton().material())
                    .lore(cfg.warehouseGui().managerButton().lore())
            )
            .action((event, self, isClicked) -> {
                // TODO: Clean this up.
                Player player = (Player) event.getWhoClicked();
                ItemStack clickedItem = event.getCurrentItem();
                Inventory clickedInventory = event.getClickedInventory();
                if (clickedItem == null) {
                    return;
                }
                ItemStack iconItem = Arrays.stream(event.getInventory().getContents())
                        .filter(Objects::nonNull)
                        .filter(item -> Util.hasPersistentKey(item, self.key()))
                        .findFirst().orElse(null);


                if (event.getClick() == ClickType.SHIFT_LEFT && isClicked) {
                    WarehouseMode mode = maltsPlayer.getWarehouseMode();
                    WarehouseMode nextMode = WarehouseMode.getNextMode(mode, player);
                    if (nextMode == mode) return;

                    maltsPlayer.setWarehouseMode(nextMode);
                    event.getInventory().setItem(cfg.warehouseGui().managerButton().slot(), self.getItemStack());
                    lng.entry(l -> l.warehouse().changedMode(), player, Couple.of("{mode}", Util.formatEnumerator(maltsPlayer.getWarehouseMode())));
                    return;
                }

                ItemConfirmation itemConfirmation = new ItemConfirmation(iconItem);

                if (!itemConfirmation.isConfirmed()) {
                    if (isClicked) {
                        itemConfirmation.setConfirmation(!itemConfirmation.isConfirmed());
                        state = event.getClick();
                    }
                    return;
                }

                Material material = clickedItem.getType();
                switch (state) {
                    case LEFT -> {
                        if (clickedInventory == event.getInventory()) {
                            break;
                        }

                        if (!warehouse.canStock(material)) {
                            lng.entry(l -> l.warehouse().blacklistedItem(), player, Couple.of("{material}", Util.formatEnumerator(material)));
                        } else if (!warehouse.hasCompartment(material)) {
                            warehouse.stockItem(material, 0);
                            refresh(player);
                        } else {
                            lng.entry(l -> l.warehouse().compartmentAlreadyExists(), player,
                                    Couple.of("{material}", Util.formatEnumerator(material)));
                        }
                    }
                    case RIGHT -> {
                        if (clickedInventory != event.getInventory()) {
                            break;
                        }
                        TriState triState = warehouse.removeCompartment(material);
                        if (triState == TriState.TRUE) {
                            lng.entry(l -> l.warehouse().removedCompartment(), player,
                                    Couple.of("{material}", Util.formatEnumerator(material))
                            );
                            refresh(player);
                        } else if (triState == TriState.FALSE) {
                            lng.entry(l -> l.warehouse().cannotRemoveCompartment(), player);
                        } else {
                            lng.entry(l -> l.warehouse().compartmentDoesNotExist(), player);
                        }
                    }
                }
                state = null;
                itemConfirmation.setConfirmation(false);
                event.setCancelled(true);
            })
            .build();

    @SuppressWarnings("unchecked")
    private final UncontainedGuiItem statusIcon = UncontainedGuiItem.builder()
            .index(() -> cfg.warehouseGui().statusIcon().slot())
            .itemStack(b -> b
                    .stringReplacements(
                            Couple.of("{name}", maltsPlayer.name()),
                            Couple.of("{stock}", warehouse.currentStockQuantity()),
                            Couple.of("{maxStock}", maltsPlayer.getCalculatedMaxWarehouseStock()),
                            Couple.of("{stockPercent}", warehouseUsagePercent())
                    )
                    .displayName(cfg.warehouseGui().statusIcon().name())
                    .lore(cfg.warehouseGui().statusIcon().lore())
                    .material(cfg.warehouseGui().statusIcon().material())
                    .headOwner(cfg.warehouseGui().statusIcon().headOwner())
            )
            .action((event, self, isClicked) -> {
                ItemStack clickedItem = event.getCurrentItem();
                Inventory inv = event.getInventory();
                if (event.getClickedInventory() == inv && !ItemStacks.borderItem().isSimilar(clickedItem)) {
                    Executors.delayedSync(1, () -> inv.setItem(cfg.warehouseGui().statusIcon().slot(), self.getItemStack()));
                }
            })
            .build();

    public WarehouseGui(Warehouse warehouse, MaltsPlayer maltsPlayer) {
        super(cfg.warehouseGui().title(), cfg.warehouseGui().size());
        this.maltsPlayer = maltsPlayer;
        this.warehouse = warehouse;

        this.autoRegister(false);

        List<ItemStack> itemStacks = new ArrayList<>();
        for (GuiItem guiItem : warehouse.stockAsGuiItems(-1)) {
            itemStacks.add(guiItem.getItemStack());
            addGuiItem(guiItem);
        }

        IntPair slots = cfg.warehouseGui().warehouseItem().slots();
        List<Integer> ignoredSlots = cfg.warehouseGui().warehouseItem().ignoredSlots();

        if (cfg.warehouseGui().borders()) {
            int i = 0;
            for (ItemStack itemStack : inventory.getContents()) {
                if (itemStack == null && (!slots.includes(i) || ignoredSlots.contains(i))) {
                    inventory.setItem(i, ItemStacks.borderItem());
                }
                i++;
            }
        }

        this.paginatedGui = PaginatedGui.builder()
                .name(cfg.warehouseGui().title())
                .items(itemStacks)
                .startEndSlots(slots.a(), slots.b())
                .ignoredSlots(ignoredSlots)
                .base(this.getInventory())
                .build();
    }

    @Override
    public void onPreInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (warehouse.isExpired()) {
            lng.entry(l -> l.gui().viewExpired(), player);
            event.setCancelled(true);
            player.closeInventory();
            return;
        }
        super.onPreInventoryClick(event);
    }

    @Override
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Config.QuickReturn quickReturn = ConfigManager.get(Config.class).quickReturn();

        event.setCancelled(true);
        MaltsPlayer maltsPlayer = DataSource.getInstance().cachedObject(player.getUniqueId(), MaltsPlayer.class);
        QuickReturnClickType quickReturnClickType = maltsPlayer.getQuickReturnClickType();

        if (event.getClickedInventory() == null && quickReturn.enabled() && quickReturnClickType != null && quickReturnClickType.getBacking() == event.getClick()) {
            YourVaultsGui gui = new YourVaultsGui(maltsPlayer);
            gui.open(player);
        }
    }


    @Override
    public void openImpl(Player player) {
        player.openInventory(this.paginatedGui.getPage(0));
    }

    private void refresh(Player player) {
        WarehouseGui newGui = new WarehouseGui(warehouse, maltsPlayer);
        newGui.open(player);
    }

    private double warehouseUsagePercent() {
        int dem = maltsPlayer.getCalculatedMaxWarehouseStock();
        if (dem == 0) {
            return 0;
        }
        return ((double) warehouse.currentStockQuantity() / dem) * 100.0;
    }
}
