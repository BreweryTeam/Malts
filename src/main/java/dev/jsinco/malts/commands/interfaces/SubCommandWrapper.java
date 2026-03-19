package dev.jsinco.malts.commands.interfaces;

import dev.jsinco.malts.Malts;
import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.files.Lang;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class SubCommandWrapper implements TabExecutor {

    private final SubCommand subCommand;

    public SubCommandWrapper(SubCommand subCommand) {
        this.subCommand = subCommand;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        Lang lang = ConfigManager.get(Lang.class);
        if (subCommand.permission() != null && !sender.hasPermission(subCommand.permission())) {
            lang.entry(l -> l.command().base().noPermission(), sender);
            return true;
        } else if (subCommand.playerOnly() && !(sender instanceof Player)) {
            lang.entry(l -> l.command().base().playerOnly(), sender);
            return true;
        }

        boolean result = subCommand.execute(sender, label, List.of(args));
        if (!result) {
            lang.entry(l -> l.command().base().invalidUsage(), sender);
        }
        return result;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (subCommand.permission() != null && !sender.hasPermission(subCommand.permission())) {
            return null;
        }
        return subCommand.tabComplete(sender, label, List.of(args));
    }

    public String name() {
        return subCommand.name();
    }
}
