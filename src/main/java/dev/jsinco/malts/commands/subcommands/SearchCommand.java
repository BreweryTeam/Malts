package dev.jsinco.malts.commands.subcommands;

import dev.jsinco.malts.commands.interfaces.ArgumentFlagReader;
import dev.jsinco.malts.commands.interfaces.SubCommand;
import dev.jsinco.malts.model.Vault;
import dev.jsinco.malts.model.VaultContentScanner;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.utility.Couple;
import dev.jsinco.malts.utility.Util;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


public class SearchCommand implements SubCommand {

    public static final String SEARCH_OTHERS_PERMISSION = "malts.searchother";

    @Override
    public boolean execute(CommandSender sender, String label, List<String> args) {
        if (args.isEmpty()) return false;

        DataSource dataSource = DataSource.getInstance();
        ArgumentFlagReader argumentFlagReader = new ArgumentFlagReader(args);
        int page = Math.max(argumentFlagReader.getFlagValueAs("page", 1, Integer.class), 1);
        OfflinePlayer player = getPlayer(sender, argumentFlagReader.getFlagValue("player"));
        String searchTerm = String.join(" ", argumentFlagReader.getNewArguments());

        if (player == null) {
            LANG.entry(l -> l.command().search().playerNotFound(), sender);
            return true;
        }

        dataSource.getVaults(player.getUniqueId()).thenAccept(snapshotVaults -> {
            String name = player != sender ? player.getName() : null;
            List<Vault> vaults = snapshotVaults.stream()
                    .map(v -> v.toVault().join())
                    .filter(v -> {
                        if (sender instanceof Player p) {
                            return v.canAccess(p);
                        }
                        return true; // Console can access all vaults
                    })
                    .toList();

            if (vaults.isEmpty()) {
                LANG.entry(l -> l.command().search().noAccessibleVaults(), sender, Couple.of("{name}", name));
                return;
            }

            VaultContentScanner scanner = new VaultContentScanner(vaults, page, name);
            VaultContentScanner.ResultCollection results = scanner.matchingVaults(searchTerm);
            sender.sendMessage(results.queryResultSummary());
        });
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, List<String> args) {
        if (args.size() > 1) {
            ArgumentFlagReader reader = new ArgumentFlagReader(List.of(args.get(args.size() - 2)));

            String pageArg = reader.getFlagValue("page");
            if (pageArg != null) {
                return Util.tryGetNextNumberArg(pageArg);
            } else if (reader.getFlagValue("player") != null) {
                return null; // Let Bukkit handle player name completion
            }
        }

        // Super lazy but whatever lolol
        List<String> completions = new ArrayList<>();
        if (!args.contains("-page")) {
            completions.add("-page");
        }
        if (!args.contains("-player") && sender.hasPermission(SEARCH_OTHERS_PERMISSION)) {
            completions.add("-player");
        }
        return completions;
    }

    @Override
    public String permission() {
        return "malts.command.search";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public String name() {
        return "search";
    }

    @Nullable
    private OfflinePlayer getPlayer(CommandSender sender, @Nullable String otherPlayer) {
        if (otherPlayer != null && sender.hasPermission(SEARCH_OTHERS_PERMISSION)) {
            return Bukkit.getOfflinePlayer(otherPlayer);
        } else if (sender instanceof Player player) {
            return player;
        }
        return null;
    }
}
