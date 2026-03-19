package dev.jsinco.malts.commands.subcommands;

import dev.jsinco.malts.Malts;
import dev.jsinco.malts.commands.interfaces.SubCommand;
import dev.jsinco.malts.gui.WarehouseGui;
import dev.jsinco.malts.model.MaltsPlayer;
import dev.jsinco.malts.model.Warehouse;
import dev.jsinco.malts.storage.DataSource;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class WarehouseAdminCommand implements SubCommand {
    @Override
    public boolean execute(CommandSender sender, String label, List<String> args) {
        if (args.isEmpty()) return false;
        Player player = (Player) sender;
        OfflinePlayer target = Bukkit.getOfflinePlayer(args.getFirst());
        DataSource dataSource = DataSource.getInstance();

        CompletableFuture<MaltsPlayer> playerFuture = dataSource.cacheObjectWithDefaultExpire(dataSource.getMaltsPlayer(target.getUniqueId()));
        CompletableFuture<Warehouse> warehouseFuture = dataSource.cacheObjectWithDefaultExpire(dataSource.getWarehouse(target.getUniqueId()));

        CompletableFuture.allOf(playerFuture, warehouseFuture).thenRunAsync(() -> {
            MaltsPlayer maltsPlayer = playerFuture.join();
            Warehouse warehouse = warehouseFuture.join();

            WarehouseGui warehouseGui = new WarehouseGui(warehouse, maltsPlayer);
            warehouseGui.open(player);
        });
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, List<String> args) {
        return null;
    }

    @Override
    public String permission() {
        return "malts.command.warehouseadmin";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public String name() {
        return "warehouseadmin";
    }
}
