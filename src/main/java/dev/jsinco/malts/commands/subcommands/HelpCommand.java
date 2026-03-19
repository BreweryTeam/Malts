package dev.jsinco.malts.commands.subcommands;

import dev.jsinco.malts.Malts;
import dev.jsinco.malts.commands.interfaces.SubCommand;
import dev.jsinco.malts.utility.Couple;
import io.papermc.paper.plugin.configuration.PluginMeta;
import org.bukkit.command.CommandSender;

import java.util.List;

public class HelpCommand implements SubCommand {

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public boolean execute(CommandSender sender, String label, List<String> args) {
        PluginMeta meta = Malts.getInstance().getPluginMeta();

        LANG.entry(l -> l.command().help(), sender,
                Couple.of("{version}", meta.getVersion()),
                Couple.of("{description}", meta.getDescription()),
                Couple.of("{authors}", meta.getAuthors())
        );
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, List<String> args) {
        return List.of();
    }

    @Override
    public String permission() {
        return "malts.command.help";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public String name() {
        return "help";
    }
}
