package dev.jsinco.malts.commands.subcommands;

import dev.jsinco.malts.Malts;
import dev.jsinco.malts.commands.interfaces.SubCommand;
import dev.jsinco.malts.gui.VaultOtherGui;
import dev.jsinco.malts.model.MaltsPlayer;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.utility.Couple;
import dev.jsinco.malts.utility.Util;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class VaultOtherCommand implements SubCommand {
    @Override
    public boolean execute(CommandSender sender, String label, List<String> args) {
        if (args.isEmpty()) return false;
        Player player = (Player) sender;
        OfflinePlayer target = Bukkit.getOfflinePlayer(args.getFirst());
        DataSource dataSource = DataSource.getInstance();

        if (args.size() < 2) {
            VaultOtherGui vaultOtherGui = new VaultOtherGui(player, target);
            vaultOtherGui.open(player);
            return true;
        }

        int vaultId = Util.getInteger(args.get(1), 1);

        dataSource.getVault(target.getUniqueId(), vaultId, false).thenAccept(vault -> {
            if (vault == null) {
                LANG.entry(l -> l.vaults().noVaultFound(), player, Couple.of("{id}", vaultId));
                return;
            } else if (!vault.canAccess(player)) {
                LANG.entry(l -> l.vaults().noAccess(), player, Couple.of("{id}", vaultId));
                return;
            }

            vault.open(player);
            LANG.entry(l -> l.vaults().opening(),
                    player,
                    Couple.of("{id}", vaultId),
                    Couple.of("{vaultName}", vault.getCustomName())
            );
        });
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, List<String> args) {
        if (args.size() <= 1) {
            return null;
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(args.getFirst());
        DataSource dataSource = DataSource.getInstance();

        /* Normally this method will not return null, but this player may not be online,
        so we'll just return nothing if that's the case. */
        MaltsPlayer maltsPlayer = dataSource.cachedObject(offlinePlayer.getUniqueId(), MaltsPlayer.class);
        if (maltsPlayer == null) {
            return Util.tryGetNextNumberArg(args.get(1));
        }
        // List of vault IDs the player has access to
        return IntStream.rangeClosed(1, maltsPlayer.getCalculatedMaxVaults())
                .mapToObj(String::valueOf)
                .collect(Collectors.toList());
    }

    @Override
    public String permission() {
        return "malts.command.vaultother";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public String name() {
        return "vaultother";
    }

}
