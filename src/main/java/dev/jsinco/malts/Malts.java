package dev.jsinco.malts;

import dev.jsinco.malts.commands.MaltsBaseCommand;
import dev.jsinco.malts.commands.VaultNameBaseCommand;
import dev.jsinco.malts.commands.VaultOtherBaseCommand;
import dev.jsinco.malts.commands.VaultSearchBaseCommand;
import dev.jsinco.malts.commands.VaultsBaseCommand;
import dev.jsinco.malts.commands.WarehouseBaseCommand;
import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.events.ChatPromptInputListener;
import dev.jsinco.malts.events.GuiListener;
import dev.jsinco.malts.events.PlayerListener;
import dev.jsinco.malts.events.VaultListener;
import dev.jsinco.malts.events.WarehouseListener;
import dev.jsinco.malts.integration.Integration;
import dev.jsinco.malts.model.MaltsInventory;
import dev.jsinco.malts.registry.Registry;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.utility.ExceptionUtil;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

public class Malts extends JavaPlugin {

    @Getter
    private static Malts instance;
    @Getter
    private static boolean shutdown;
    @Getter
    @Setter
    private static boolean invalidatedCachedGuiItems;

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        DataSource.createInstance();
        ConfigManager.createTranslationConfigs();
        // Needs to be here to prevent lazy init
        Registry<Integration> ignored = Registry.INTEGRATIONS;

        getServer().getPluginCommand("malts").setExecutor(new MaltsBaseCommand());
        getServer().getPluginCommand("vaults").setExecutor(new VaultsBaseCommand());
        getServer().getPluginCommand("warehouse").setExecutor(new WarehouseBaseCommand());
        getServer().getPluginCommand("vaultother").setExecutor(new VaultOtherBaseCommand());
        getServer().getPluginCommand("vaultname").setExecutor(new VaultNameBaseCommand());
        getServer().getPluginCommand("vaultsearch").setExecutor(new VaultSearchBaseCommand());

        DataSource dataSource = DataSource.getInstance();
        for (Player player : getServer().getOnlinePlayers()) {
            dataSource.cacheObject(dataSource.getMaltsPlayer(player.getUniqueId()));
            dataSource.cacheObject(dataSource.getWarehouse(player.getUniqueId()));
        }

        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        getServer().getPluginManager().registerEvents(new VaultListener(), this);
        getServer().getPluginManager().registerEvents(new WarehouseListener(), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(new ChatPromptInputListener(), this);
    }

    @Override
    public void onDisable() {
        shutdown = true;
        DataSource dataSource = DataSource.getInstance();

        Registry.INTEGRATIONS.values().forEach(integration ->
                ExceptionUtil.unsafe(integration::unregister)
        );


        for (Player player : getServer().getOnlinePlayers()) {
            Inventory inv = player.getOpenInventory().getTopInventory();
            if (inv.getHolder(false) instanceof MaltsInventory) {
                player.closeInventory();
            }
        }

        dataSource.close();
    }
}