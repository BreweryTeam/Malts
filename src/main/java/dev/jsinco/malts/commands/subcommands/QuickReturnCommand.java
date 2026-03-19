package dev.jsinco.malts.commands.subcommands;

import dev.jsinco.malts.Malts;
import dev.jsinco.malts.commands.interfaces.SubCommand;
import dev.jsinco.malts.enums.QuickReturnClickType;
import dev.jsinco.malts.model.MaltsPlayer;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.utility.Util;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class QuickReturnCommand implements SubCommand {
    @Override
    public boolean execute(CommandSender sender, String label, List<String> args) {
        if (args.isEmpty()) {
            LANG.entry(l -> l.command().quickReturn().failed(), sender);
            return false;
        }

        Player player = (Player) sender;
        MaltsPlayer maltsPlayer = DataSource.getInstance().cachedObject(player.getUniqueId(), MaltsPlayer.class);

        QuickReturnClickType newQuickReturnType = Util.getEnum(args.getFirst(), QuickReturnClickType.class);
        if (newQuickReturnType == null) {
            LANG.entry(l -> l.command().quickReturn().failed(), player);
            return false;
        }

        maltsPlayer.setQuickReturnClickType(newQuickReturnType);
        LANG.entry(l -> l.command().quickReturn().success(), player);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, List<String> args) {
        return Arrays.stream(QuickReturnClickType.values())
                .map(it -> it.name().toLowerCase())
                .toList();
    }

    @Override
    public String permission() {
        return "malts.command.quickreturn";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public String name() {
        return "quickreturn";
    }
}
