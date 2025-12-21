package dev.jsinco.malts.commands.subcommands;

import com.google.common.base.Preconditions;
import dev.jsinco.malts.Malts;
import dev.jsinco.malts.commands.interfaces.SubCommand;
import dev.jsinco.malts.gui.YourVaultsGui;
import dev.jsinco.malts.model.MaltsPlayer;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.utility.Couple;
import dev.jsinco.malts.utility.Util;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class VaultsCommand implements SubCommand {

    @Override
    public boolean execute(CommandSender sender, String label, List<String> args) {
        Player player = (Player) sender;
        DataSource dataSource = DataSource.getInstance();
        MaltsPlayer maltsPlayer = dataSource.cachedObject(player.getUniqueId(), MaltsPlayer.class);
        Preconditions.checkNotNull(maltsPlayer, "MaltsPlayer should not be null for online player.");

        if (args.isEmpty()) {
            YourVaultsGui yourVaultsGui = new YourVaultsGui(maltsPlayer);
            yourVaultsGui.open(player);
            return true;
        }

        int vaultId = Util.getInteger(args.getFirst(), 1);

        if (maltsPlayer.getCalculatedMaxVaults() < vaultId) {
            LANG.entry(l -> l.vaults().noAccess(), player, Couple.of("{id}", vaultId));
            return true;
        }

        dataSource.getVaultWithEconomy(player, vaultId, vault -> {
            vault.open(player);
            LANG.entry(l -> l.vaults().opening(), player,
                    Couple.of("{id}", vaultId),
                    Couple.of("{vaultName}", vault.getCustomName())
            );
        });
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, List<String> args) {
        Player player = (Player) sender;
        DataSource dataSource = DataSource.getInstance();
        MaltsPlayer maltsPlayer = dataSource.cachedObject(player.getUniqueId(), MaltsPlayer.class);
        Preconditions.checkNotNull(maltsPlayer, "MaltsPlayer should not be null for online player.");
        // List of vault IDs the player has access to
        return IntStream.rangeClosed(1, maltsPlayer.getCalculatedMaxVaults())
                .mapToObj(String::valueOf)
                .collect(Collectors.toList());
    }

    @Override
    public String name() {
        return "vaults";
    }

    @Override
    public String permission() {
        return "malts.command.vaults";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

}
