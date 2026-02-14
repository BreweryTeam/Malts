package dev.jsinco.malts.gui;

import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.files.GuiConfig;
import dev.jsinco.malts.configuration.IntPair;
import dev.jsinco.malts.configuration.files.Lang;
import dev.jsinco.malts.gui.item.GuiItem;
import dev.jsinco.malts.obj.MaltsPlayer;
import dev.jsinco.malts.obj.OtherPlayerSnapshotVault;
import dev.jsinco.malts.obj.SnapshotVault;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.utility.Couple;
import dev.jsinco.malts.utility.Executors;
import dev.jsinco.malts.utility.ItemStacks;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class VaultOtherGui extends MaltsGui implements PromisedInventory {

    private static final GuiConfig cfg = ConfigManager.get(GuiConfig.class);
    private static final Lang lng = ConfigManager.get(Lang.class);

    private PaginatedGui paginatedGui;

    private final Player viewer;
    private final OfflinePlayer target;

    private final GuiItem previousPage = GuiItem.builder()
            .index(() -> cfg.vaultOtherGui().previousPage().slot())
            .itemStack(b -> b
                    .displayName(cfg.vaultOtherGui().previousPage().title())
                    .material(cfg.vaultOtherGui().previousPage().material())
                    .lore(cfg.vaultOtherGui().previousPage().lore())
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
            .index(() -> cfg.vaultOtherGui().nextPage().slot())
            .itemStack(b -> b
                    .displayName(cfg.vaultOtherGui().nextPage().title())
                    .material(cfg.vaultOtherGui().nextPage().material())
                    .lore(cfg.vaultOtherGui().nextPage().lore())
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



    public VaultOtherGui(Player viewer, OfflinePlayer target) {
        super(cfg.vaultOtherGui().title(), cfg.vaultOtherGui().size());
        this.viewer = viewer;
        this.target = target;

        this.autoRegister(false);

        IntPair slots = cfg.vaultOtherGui().vaultItem().slots();
        List<Integer> ignoredSlots = cfg.vaultOtherGui().vaultItem().ignoredSlots();
        if (cfg.vaultOtherGui().borders()) {
            for (int i = 0; i < this.inventory.getSize(); i++) {
                ItemStack itemStack = this.inventory.getItem(i);
                if (itemStack != null || (slots.includes(i) && !ignoredSlots.contains(i))) continue;
                this.inventory.setItem(i, ItemStacks.borderItem());
            }
        }
    }

    @Override
    public void onInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
    }

    @Override
    public CompletableFuture<@Nullable Inventory> promiseInventory() {
        DataSource dataSource = DataSource.getInstance();
        return dataSource.getVaults(target.getUniqueId()).thenCompose(unfilteredSnapshotVaults -> {
            if (unfilteredSnapshotVaults.isEmpty()) {
                lng.entry(l -> l.vaults().noVaultsFound(), viewer, Couple.of("{name}", targetName()));
                return CompletableFuture.completedFuture(null);
            }

            List<OtherPlayerSnapshotVault> snapshotVaults = unfilteredSnapshotVaults.stream()
                    .filter(it -> it.canAccess(viewer))
                    .map(OtherPlayerSnapshotVault::new)
                    .sorted(Comparator.comparingInt(SnapshotVault::getId))
                    .toList();

            if (snapshotVaults.isEmpty()) {
                lng.entry(l -> l.vaults().noVaultsAccessible(), viewer, Couple.of("{name}", targetName()));
                return CompletableFuture.completedFuture(null);
            }

            CompletableFuture<MaltsPlayer> playerFuture;

            MaltsPlayer cached = dataSource.cachedObject(target.getUniqueId(), MaltsPlayer.class);
            playerFuture = cached != null ? CompletableFuture.completedFuture(cached) : dataSource.getMaltsPlayer(target.getUniqueId());

            return playerFuture.thenApply(targetMaltsPlayer -> {
                List<ItemStack> itemStacks = new ArrayList<>();

                for (var snapshotVault : snapshotVaults) {
                    addGuiItem(snapshotVault);
                    itemStacks.add(snapshotVault.getItemStack());
                }

                IntPair slots = cfg.vaultOtherGui().vaultItem().slots();
                this.paginatedGui = PaginatedGui.builder()
                        .name(cfg.vaultOtherGui().title().replace("{name}", targetName()))
                        .items(itemStacks)
                        .startEndSlots(slots.a(), slots.b())
                        .ignoredSlots(cfg.vaultOtherGui().vaultItem().ignoredSlots())
                        .base(this.inventory)
                        .build();

                return this.paginatedGui.getPage(0);
            });
        });
    }

    @Override
    public void openImpl(Player player) {
        promiseInventory().thenAccept(inventory -> {
            if (inventory != null) {
                Executors.runSync(player, () -> player.openInventory(inventory));
            }
        });
    }

    private String targetName() {
        return target.getName() != null ? target.getName() : "Unknown";
    }


}
