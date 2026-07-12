package dev.jsinco.malts.commands.subcommands;

import dev.jsinco.malts.commands.interfaces.SubCommand;
import dev.jsinco.malts.logging.MaltsLogger;
import dev.jsinco.malts.logging.dialog.LogDialog;
import dev.jsinco.malts.utility.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Stream;

public class LogsCommand implements SubCommand {

    private static final boolean DIALOGS_SUPPORTED = probeDialogSupport();
    private static boolean probeDialogSupport() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean execute(CommandSender sender, String label, List<String> args) {
        String action = args.isEmpty() ? "view" : args.getFirst().toLowerCase();
        return switch (action) {
            case "flush" -> flush(sender);
            case "view" -> view(sender);
            default -> false;
        };
    }

    private boolean flush(CommandSender sender) {
        MaltsLogger logger = MaltsLogger.get();
        if (logger == null) {
            Text.msg(sender, "<red>The logging system is not available.");
            return true;
        }
        logger.flushAsync().thenRun(() -> Text.msg(sender, "<green>Flushed buffered log entries to disk."));
        return true;
    }

    private boolean view(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            LANG.entry(l -> l.command().base().playerOnly(), sender);
            return true;
        }
        if (!DIALOGS_SUPPORTED) {
            Text.msg(sender, "<red>The in-game log viewer requires a server running Minecraft 1.21.6 or newer. "
                    + "Use <white>/malts logs flush</white> and read the files in <white>plugins/Malts/logs</white> instead.");
            return true;
        }
        MaltsLogger logger = MaltsLogger.get();
        if (logger == null) {
            Text.msg(sender, "<red>The logging system is not available.");
            return true;
        }
        LogDialog.open(player, logger);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, List<String> args) {
        if (args.size() == 1) {
            return Stream.of("view", "flush")
                    .filter(it -> it.startsWith(args.getFirst().toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    @Override
    public String permission() {
        return "malts.command.logs";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public String name() {
        return "logs";
    }
}
