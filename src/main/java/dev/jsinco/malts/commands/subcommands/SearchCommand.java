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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;


public class SearchCommand implements SubCommand {

    public static final String SEARCH_OTHERS_PERMISSION = "malts.searchother";

    @Override
    public boolean execute(CommandSender sender, String label, List<String> args) {
        if (args.isEmpty()) return false;

        DataSource dataSource = DataSource.getInstance();
        ArgumentFlagReader argumentFlagReader = new ArgumentFlagReader(args);
        int page = Math.max(argumentFlagReader.getFlagValueAs("page", 1, Integer.class), 1);
        String playerName = argumentFlagReader.getFlagValue("player");
        boolean allVaults = "*".equals(playerName) && sender.hasPermission(SEARCH_OTHERS_PERMISSION);
        OfflinePlayer player = allVaults ? null : getPlayer(sender, playerName);
        String searchTerm = String.join(" ", argumentFlagReader.getNewArguments());

        CompletableFuture<Collection<Vault>> vaults;

        if (allVaults) {
            vaults = dataSource.getAllVaultsNow();
        } else {
            if (player == null) {
                LANG.entry(l -> l.command().search().playerNotFound(), sender);
                return true;
            }
            vaults = dataSource.getVaultsNow(player.getUniqueId());
        }


        vaults.thenAcceptAsync(actualVaults -> {
            String name = allVaults ? "*" : (player != sender ? player.getName() : null);
            List<Vault> filteredVaults = actualVaults.stream()
                    .filter(v -> {
                        if (sender instanceof Player p) {
                            return v.canAccess(p);
                        }
                        return true; // Console can access all vaults
                    })
                    .toList();

            if (filteredVaults.isEmpty()) {
                LANG.entry(l -> l.command().search().noAccessibleVaults(), sender, Couple.of("{name}", name));
                return;
            }

            VaultContentScanner scanner = new VaultContentScanner(filteredVaults, page, name,
                    sender instanceof Player p ? p.getUniqueId() : null);
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
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
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
