package dev.jsinco.malts.commands;

import dev.jsinco.malts.Malts;
import dev.jsinco.malts.commands.subcommands.HelpCommand;
import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.files.Config;
import dev.jsinco.malts.configuration.files.Lang;
import dev.jsinco.malts.registry.Registry;
import dev.jsinco.malts.commands.interfaces.SubCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MaltsBaseCommand implements TabExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 0 && sender.hasPermission("malts.command.base")) {
            String baseCommandBehavior = ConfigManager.get(Config.class).baseCommandBehavior();
            SubCommand subCommand = Registry.SUB_COMMANDS.get(baseCommandBehavior);
            if (subCommand == null || (subCommand.playerOnly() && !(sender instanceof Player)) ||
                    (subCommand.permission() != null && !sender.hasPermission(subCommand.permission()))) {
                subCommand = Registry.SUB_COMMANDS.get(HelpCommand.class);
            }
            return subCommand.execute(sender, label, new ArrayList<>());
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = Registry.SUB_COMMANDS.get(subCommandName);
        Lang lang = ConfigManager.get(Lang.class);

        if (subCommand == null) {
            lang.entry(l -> l.command().base().unknownCommand(), sender);
            return true;
        } else if (subCommand.playerOnly() && !(sender instanceof Player)) {
            lang.entry(l -> l.command().base().playerOnly(), sender);
            return true;
        } else if (subCommand.permission() != null && !sender.hasPermission(subCommand.permission())) {
            lang.entry(l -> l.command().base().noPermission(), sender);
            return true;
        }

        List<String> commandArgs = new ArrayList<>(List.of(args));
        commandArgs.removeFirst(); // Remove the subcommand name

        boolean result = subCommand.execute(sender, label, commandArgs);
        if (!result) {
            lang.entry(l -> l.command().base().invalidUsage(), sender);
        }
        return result;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length <= 1) {
            return Registry.SUB_COMMANDS.stream()
                    .filter(it -> {
                        String perm = it.getValue().permission();
                        return perm == null || sender.hasPermission(perm);
                    })
                    .map(Map.Entry::getKey)
                    .toList();
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = Registry.SUB_COMMANDS.get(subCommandName);

        if (subCommand == null || (subCommand.permission() != null && !sender.hasPermission(subCommand.permission()))) {
            return null;
        }

        List<String> commandArgs = new ArrayList<>(List.of(args));
        commandArgs.removeFirst();

        return subCommand.tabComplete(sender, label, commandArgs);
    }
}
