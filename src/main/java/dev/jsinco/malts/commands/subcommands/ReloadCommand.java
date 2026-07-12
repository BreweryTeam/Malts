package dev.jsinco.malts.commands.subcommands;

import dev.jsinco.malts.Malts;
import dev.jsinco.malts.commands.interfaces.SubCommand;
import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.OkaeriFile;
import dev.jsinco.malts.configuration.files.Config;
import dev.jsinco.malts.integration.compiled.UpdateCheckIntegration;
import dev.jsinco.malts.logging.MaltsLogger;
import dev.jsinco.malts.registry.Registry;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.utility.Couple;
import dev.jsinco.malts.utility.Text;
import org.bukkit.command.CommandSender;

import java.util.Comparator;
import java.util.List;

public class ReloadCommand implements SubCommand {
    @Override
    public boolean execute(CommandSender sender, String label, List<String> args) {
        boolean success = true;
        try {
            Config.Storage oldStorage = ConfigManager.get(Config.class).storage();

            ConfigManager.createTranslationConfigs();
            Registry.CONFIGS.values()
                    .stream()
                    .sorted(Comparator.comparing(OkaeriFile::isDynamicFileName))
                    .forEach(OkaeriFile::reload);

            Config.Storage newStorage = ConfigManager.get(Config.class).storage();

            if (!oldStorage.equals(newStorage)) {
                Text.log("Storage configuration has changed, re-initializing data source...");
                DataSource dataSource = DataSource.getInstance();
                dataSource.close().whenComplete((unused, throwable) -> {
                    DataSource.createInstance(newStorage);
                    LANG.entry(l -> l.command().reload().newDatabaseDriverSet(), sender, Couple.of("{driver}", newStorage.driver().toString()));
                }).exceptionally(throwable -> {
                    throwable.printStackTrace();
                    return null;
                });
            }

            MaltsLogger.init();
            Malts.setInvalidatedCachedGuiItems(true);
        } catch (Throwable e) {
            Text.error("An exception/error occurred while reloading Malts configuration", e);
            success = false;
        }

        final boolean finalSuccess = success;
        LANG.entry(l -> finalSuccess ? l.command().reload().success() : l.command().reload().failed(), sender);
        if (finalSuccess) {
            UpdateCheckIntegration updateCheck = Registry.INTEGRATIONS.get(UpdateCheckIntegration.class);
            if (updateCheck != null && updateCheck.isUpdateAvailable()) {
                updateCheck.sendUpdateMessage(sender);
            }
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, List<String> args) {
        return List.of();
    }

    @Override
    public String permission() {
        return "malts.command.reload";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public String name() {
        return "reload";
    }
}
