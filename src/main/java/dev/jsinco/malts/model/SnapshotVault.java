package dev.jsinco.malts.model;

import com.google.common.collect.ImmutableList;
import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.files.GuiConfig;
import dev.jsinco.malts.gui.EditVaultGui;
import dev.jsinco.malts.gui.item.AbstractGuiItem;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.utility.Couple;
import dev.jsinco.malts.utility.ItemStacks;
import dev.jsinco.malts.utility.Util;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static dev.jsinco.malts.model.Vault.BYPASS_OPEN_VAULT_PERM;
import static dev.jsinco.malts.model.Vault.LIST_UUID_TYPE_TOKEN;

/**
 * A snapshot of a vault's data, used for displaying in GUIs without loading the full vault.
 */
public class SnapshotVault extends AbstractGuiItem {

    private static final GuiConfig GUI_CONFIG = ConfigManager.get(GuiConfig.class);

    @Getter
    private final VaultKey key;
    @Getter
    private final String customName;
    @Getter
    private final Material icon;
    @Getter
    private final ImmutableList<UUID> trustedPlayers;

    public SnapshotVault(UUID owner, int id, String customName, Material icon) {
        this(owner, id, customName, icon, ImmutableList.of());
    }

    public SnapshotVault(UUID owner, int id, String customName, Material icon, ImmutableList<UUID> trustedPlayers) {
        this.key = VaultKey.of(owner, id);
        this.customName = customName != null && !customName.isEmpty() ? customName : "Vault #" + id;
        this.icon = icon != null && icon.isItem() ? icon : Material.CHEST;
        this.trustedPlayers = trustedPlayers;
    }


    public SnapshotVault(UUID owner, int id, String customName, Material icon, String trustedPlayers) {
        this.key = VaultKey.of(owner, id);
        this.customName = customName != null && !customName.isEmpty() ? customName : "Vault #" + id;
        this.icon = icon != null && icon.isItem() ? icon : Material.CHEST;

        List<UUID> json = Util.GSON.fromJson(trustedPlayers, LIST_UUID_TYPE_TOKEN);
        this.trustedPlayers = json != null ? ImmutableList.copyOf(json) : ImmutableList.of();
    }


    public CompletableFuture<Vault> toVault() {
        DataSource dataSource = DataSource.getInstance();
        return dataSource.getVault(this.key.owner(), this.key.id());
    }

    public void toVaultWithEconomy(Player player, Consumer<Vault> consumer) {
        DataSource dataSource = DataSource.getInstance();
        dataSource.getVaultWithEconomy(player, this.key.id(), consumer);
    }

    public boolean isTrusted(UUID uuid) {
        return trustedPlayers.contains(uuid);
    }

    public boolean canAccess(Player player) {
        return player.getUniqueId() == this.key.owner() || this.trustedPlayers.contains(player.getUniqueId()) || player.hasPermission(BYPASS_OPEN_VAULT_PERM);
    }

    @SuppressWarnings("unchecked")
    @Override
    public ItemStack itemStack() {
        return ItemStacks.builder()
                .stringReplacements(
                        Couple.of("{vaultName}", customName),
                        Couple.of("{id}", this.key.id())
                )
                .displayName(GUI_CONFIG.yourVaultsGui().vaultItem().name())
                .material(icon)
                .lore(GUI_CONFIG.yourVaultsGui().vaultItem().lore())
                .build();
    }

    @Override
    public void onClick(InventoryClickEvent event, ItemStack clickedItem) {
        if (!Util.hasPersistentKey(clickedItem, key())) {
            return;
        }

        final boolean isLeftClick = event.isLeftClick();
        Player player = (Player) event.getWhoClicked();

        toVaultWithEconomy(player, vault -> {
            if (isLeftClick) {
                vault.open(player);
            } else {
                MaltsPlayer maltsPlayer = DataSource.getInstance().cachedObject(player.getUniqueId(), MaltsPlayer.class);
                EditVaultGui editVaultGui = new EditVaultGui(vault, maltsPlayer, player);
                editVaultGui.open(player);
            }
        });
    }

    public UUID getOwner() {
        return this.key.owner();
    }

    public int getId() {
        return this.key.id();
    }
}
