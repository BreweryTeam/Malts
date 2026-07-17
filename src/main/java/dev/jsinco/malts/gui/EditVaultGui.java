package dev.jsinco.malts.gui;

import com.google.common.base.Preconditions;
import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.files.GuiConfig;
import dev.jsinco.malts.configuration.files.Lang;
import dev.jsinco.malts.events.ChatPromptInputListener.ChatInputCallback;
import dev.jsinco.malts.gui.item.GuiItem;
import dev.jsinco.malts.gui.item.ItemConfirmation;
import dev.jsinco.malts.gui.item.UncontainedGuiItem;
import dev.jsinco.malts.logging.MaltsLogger;
import dev.jsinco.malts.model.MaltsPlayer;
import dev.jsinco.malts.model.Vault;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.utility.Couple;
import dev.jsinco.malts.utility.ItemStacks;
import dev.jsinco.malts.utility.Text;
import dev.jsinco.malts.utility.Util;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class EditVaultGui extends MaltsGui {

    private static final GuiConfig CONFIG = ConfigManager.get(GuiConfig.class);
    private static final Lang LANG = ConfigManager.get(Lang.class);

    private Vault vault;
    private MaltsPlayer maltsPlayer;
    private Player player;

    private final GuiItem backButton = GuiItem.builder()
            .index(() -> CONFIG.editVaultGui().backButton().slot())
            .itemStack(b -> b
                    .displayName(CONFIG.editVaultGui().backButton().name())
                    .material(CONFIG.editVaultGui().backButton().material())
                    .lore(CONFIG.editVaultGui().backButton().lore())
            )
            .action(e -> {
                Player player = (Player) e.getWhoClicked();
                YourVaultsGui yourVaultsGui = new YourVaultsGui(maltsPlayer);
                yourVaultsGui.open(player);
            })
            .build();
    @SuppressWarnings("unchecked")
    private final GuiItem editNameButton = GuiItem.builder()
            .index(() -> CONFIG.editVaultGui().editNameButton().slot())
            .itemStack(b -> b
                    .stringReplacements(
                            Couple.of("{vaultName}", vault.getCustomName()),
                            Couple.of("{id}", String.valueOf(vault.getId()))
                    )
                    .displayName(CONFIG.editVaultGui().editNameButton().name())
                    .material(CONFIG.editVaultGui().editNameButton().material())
                    .lore(CONFIG.editVaultGui().editNameButton().lore())
            )
            .action(event -> {
                ItemStack clickedItem = event.getCurrentItem();
                Player player = (Player) event.getWhoClicked();
                player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);

                ChatInputCallback.of(
                        player,
                        Text.title("<red><b>Enter text", "Enter in chat"),
                        "Enter a new vault name in chat, type 'cancel' to cancel.",
                        input -> {
                            String oldName = vault.getCustomName();
                            if (!vault.setCustomName(input)) return;
                            DataSource.getInstance().saveVault(vault);

                            MaltsLogger.logger().logVaultName(player, vault, oldName, vault.getCustomName());
                            LANG.entry(l -> l.vaults().nameChanged(), player, Couple.of("{vaultName}", vault.getCustomName()));
                            Util.editMeta(clickedItem, meta -> {
                                meta.lore(Text.mmlNoItalic(Util.replaceAll(CONFIG.editVaultGui().editNameButton().lore(), "{vaultName}", vault.getCustomName()), NamedTextColor.WHITE));
                            });
                            open(player);
                        },
                        () -> open(player)
                );
            })
            .build();

    private final UncontainedGuiItem editIconButton = UncontainedGuiItem.builder()
            .index(() -> CONFIG.editVaultGui().editIconButton().slot())
            .itemStack(b -> b
                    .displayName(CONFIG.editVaultGui().editIconButton().name())
                    .material(CONFIG.editVaultGui().editIconButton().material())
                    .lore(CONFIG.editVaultGui().editIconButton().lore())
            )
            .action((event, self, isClicked) -> {
                ItemStack clickedItem = event.getCurrentItem();
                ItemStack iconItem = Arrays.stream(event.getInventory().getContents())
                        .filter(Objects::nonNull)
                        .filter(item -> Util.hasPersistentKey(item, self.key()))
                        .findFirst().orElse(null);


                ItemConfirmation itemConfirmation = new ItemConfirmation(iconItem);
                boolean currentValue = itemConfirmation.isConfirmed();
                Inventory clickedInventory = event.getClickedInventory();


                if (isClicked) {
                    itemConfirmation.setConfirmation(!currentValue);
                } else if (currentValue && clickedInventory != event.getInventory()) {
                    Material newType = clickedItem.getType();
                    Material oldIcon = vault.getIcon();
                    if (!vault.setIcon(newType)) return;

                    DataSource.getInstance().saveVault(vault);

                    MaltsLogger.logger().logVaultIcon((Player) event.getWhoClicked(), vault, oldIcon, newType);
                    iconItem.setType(newType); // TODO: deprecated method
                    itemConfirmation.setConfirmation(false);
                    LANG.entry(l -> l.vaults().iconChanged(), event.getWhoClicked(), Couple.of("{material}", Util.formatEnumerator(newType)));
                }
            })
            .build();

    @SuppressWarnings("unchecked")
    private final GuiItem editTrustedListButton = GuiItem.builder()
            .index(() -> CONFIG.editVaultGui().editTrustListButton().slot())
            .itemStack(b -> b
                    .stringReplacements(
                            Couple.of("{vaultName}", vault.getCustomName()),
                            Couple.of("{id}", String.valueOf(vault.getId())),
                            Couple.of("{name}", player.getName()),
                            Couple.of("{trustedListSize}", trustListCap(vault.getOwner())),
                            Couple.of("{name}", player.getName()),
                            Couple.of("{trustedListSize}", trustListCap(vault.getOwner()))
                            //Couple.of("{trustedList}", trustedListString()),
                    )
                    .displayName(CONFIG.editVaultGui().editTrustListButton().name())
                    .material(CONFIG.editVaultGui().editTrustListButton().material())
                    .headOwner(CONFIG.editVaultGui().editTrustListButton().headOwner())
                    .lore(Util.replaceStringWithList(CONFIG.editVaultGui().editTrustListButton().lore(), "{trustedList}", trustedListString()))
            )
            .action(event -> {
                ItemStack clickedItem = event.getCurrentItem();
                Player player = (Player) event.getWhoClicked();
                player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);

                ChatInputCallback.of(
                        player,
                        Text.title("<red><b>Enter text", "Enter in chat"),
                        "In chat, enter the username of a player. To remove an existing player from your vault, enter their name. Type 'cancel' to cancel.",
                        input -> {
                            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayerIfCached(input);

                            if (offlinePlayer == null) {
                                LANG.entry(l -> l.vaults().playerNeverOnServer(), player, Couple.of("{name}", input));
                                open(player);
                                return;
                            }

                            MaltsLogger logger = MaltsLogger.logger();
                            if (vault.isTrusted(offlinePlayer.getUniqueId())) {
                                if (vault.removeTrusted(offlinePlayer.getUniqueId())) {
                                    LANG.entry(l -> l.vaults().playerUntrusted(), player, Couple.of("{name}", input));
                                    logger.logVaultTrust(player, vault, offlinePlayer.getUniqueId(), false);
                                } else {
                                    LANG.entry(l -> l.vaults().playerNotTrusted(), player, Couple.of("{name}", input));
                                }
                            } else if (vault.addTrusted(offlinePlayer.getUniqueId())){
                                LANG.entry(l -> l.vaults().playerTrusted(), player, Couple.of("{name}", input));
                                logger.logVaultTrust(player, vault, offlinePlayer.getUniqueId(), true);
                            } else {
                                LANG.entry(l -> l.vaults().trustListMaxed(), player, Couple.of("{trustedListSize}", trustListCap(vault.getOwner())));
                            }

                            DataSource.getInstance().saveVault(vault);

                            Util.editMeta(clickedItem, meta -> {
                                meta.displayName(Text.mmNoItalic(CONFIG.editVaultGui().editTrustListButton().name().replace("{trustedListSize}", trustListCap(vault.getOwner())), NamedTextColor.AQUA));
                                meta.lore(Text.mmlNoItalic(
                                        Util.replaceAll(
                                                Util.replaceStringWithList(CONFIG.editVaultGui().editTrustListButton().lore(), "{trustedList}", trustedListString()),
                                                "{trustedListSize}", trustListCap(vault.getOwner())
                                        ),
                                        NamedTextColor.WHITE
                                ));
                            });
                            //event.getInventory().setItem(cfg.editVaultGui().editTrustListButton().slot(), item);
                            open(player);
                        },
                        () -> open(player)
                );
            })
            .build();


    public EditVaultGui(Vault vault, MaltsPlayer maltsPlayer, Player player) {
        super(CONFIG.editVaultGui().title().replace("{vaultName}", vault.getCustomName()).replace("{id}", String.valueOf(vault.getId())), CONFIG.editVaultGui().size());
        this.vault = vault;
        this.maltsPlayer = maltsPlayer;
        this.player = player;

        this.autoRegister(false);

        if (CONFIG.editVaultGui().borders()) {
            for (int i = 0; i < this.inventory.getSize(); i++) {
                if (this.inventory.getItem(i) == null) {
                    this.inventory.setItem(i, ItemStacks.borderItem());
                }
            }
        }
    }

    @Override
    public void onInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void openImpl(Player player) {
        player.openInventory(this.getInventory());
    }

    private String trustListCap(UUID ownerUUID) {
        MaltsPlayer ownerMaltsPlayer = DataSource.getInstance().cachedObject(vault.getOwner(), MaltsPlayer.class);
        Preconditions.checkNotNull(ownerMaltsPlayer, "MaltsPlayer should not be null for vault owner.");
        int max = ownerMaltsPlayer.getTrustCapacity();
        return vault.getTrustedPlayers().size() + "/" + max;
    }

    private List<String> trustedListString() {
        return vault.getTrustedPlayers().stream().map(id -> {
            String name = Bukkit.getOfflinePlayer(id).getName();
            return "- " + (name != null ? name : id.toString());
        }).toList();
    }
}
