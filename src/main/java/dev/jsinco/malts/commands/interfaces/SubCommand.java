package dev.jsinco.malts.commands.interfaces;

import dev.jsinco.malts.Malts;
import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.files.Lang;
import dev.jsinco.malts.registry.RegistryItem;
import org.bukkit.command.CommandSender;

import java.util.List;

public interface SubCommand extends RegistryItem {

    Lang LANG = ConfigManager.get(Lang.class);

    boolean execute(CommandSender sender, String label, List<String> args);

    List<String> tabComplete(CommandSender sender, String label, List<String> args);

    String permission();

    boolean playerOnly();

}
