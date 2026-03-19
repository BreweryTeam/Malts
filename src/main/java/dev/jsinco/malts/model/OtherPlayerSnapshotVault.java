package dev.jsinco.malts.model;

import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.files.GuiConfig;
import dev.jsinco.malts.utility.Couple;
import dev.jsinco.malts.utility.ItemStacks;
import dev.jsinco.malts.utility.Util;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class OtherPlayerSnapshotVault extends SnapshotVault {

    private static final GuiConfig cfg = ConfigManager.get(GuiConfig.class);

    public OtherPlayerSnapshotVault(SnapshotVault snapshotVault) {
        super(snapshotVault.getOwner(), snapshotVault.getId(), snapshotVault.getCustomName(), snapshotVault.getIcon(), snapshotVault.getTrustedPlayers());
    }

    @SuppressWarnings("unchecked")
    @Override
    public ItemStack itemStack() {
        return ItemStacks.builder()
                .stringReplacements(
                        Couple.of("{vaultName}", getCustomName()),
                        Couple.of("{id}", getId())
                )
                .displayName(cfg.vaultOtherGui().vaultItem().name())
                .material(getIcon())
                .lore(cfg.vaultOtherGui().vaultItem().lore())
                .build();
    }

    @Override
    public void onClick(InventoryClickEvent event, ItemStack clickedItem) {
        if (!Util.hasPersistentKey(clickedItem, key())) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        if (event.isLeftClick()) {
            toVault().thenAccept(vault -> vault.open(player));
        }
    }
}
