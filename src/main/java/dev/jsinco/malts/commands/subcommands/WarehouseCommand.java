package dev.jsinco.malts.commands.subcommands;

import com.google.common.base.Preconditions;
import dev.jsinco.malts.Malts;
import dev.jsinco.malts.commands.interfaces.SubCommand;
import dev.jsinco.malts.gui.WarehouseGui;
import dev.jsinco.malts.model.MaltsPlayer;
import dev.jsinco.malts.model.Warehouse;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.utility.Couple;
import dev.jsinco.malts.utility.Util;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class WarehouseCommand implements SubCommand {

    @Override
    public boolean execute(CommandSender sender, String label, List<String> args) {
        Player player = (Player) sender;
        DataSource dataSource = DataSource.getInstance();
        Warehouse warehouse = dataSource.cachedObject(player.getUniqueId(), Warehouse.class);
        MaltsPlayer maltsPlayer = dataSource.cachedObject(player.getUniqueId(), MaltsPlayer.class);


        if (args.isEmpty()) {
            WarehouseGui warehouseGui = new WarehouseGui(warehouse, maltsPlayer);
            warehouseGui.open(player);
            return true;
        } else if (args.size() < 3) {
            return false;
        }

        ArgOption option = Util.getEnum(args.getFirst(), ArgOption.class);

        if (option == null) {
            return false;
        }

        Material material = Util.getEnum(args.get(1), Material.class);
        int amount = Util.getInteger(args.get(2), 0);

        if (material == null || !material.isItem()) {
            LANG.entry(l -> l.warehouse().blacklistedItem(), player, Couple.of("{material}", Util.formatEnumerator(args.get(1))));
            return true;
        } else if (amount <= 0) {
            LANG.entry(l -> l.warehouse().notEnoughMaterial(), player, Couple.of("{material}", Util.formatEnumerator(material)));
            return true;
        }

        option.getExecutor().handle(player, maltsPlayer, warehouse, material, amount);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, List<String> args) {
        Player player = (Player) sender;
        DataSource dataSource = DataSource.getInstance();
        Warehouse warehouse = dataSource.cachedObject(player.getUniqueId(), Warehouse.class);
        Preconditions.checkNotNull(warehouse, "Warehouse cannot be null for tab completion");

        return switch (args.size()) {
            case 1 -> Arrays.stream(ArgOption.values())
                    .map(Enum::toString)
                    .map(String::toLowerCase)
                    .toList();
            case 2 -> warehouse.storedMaterials().stream()
                    .map(it -> it.toString().toLowerCase())
                    .toList();
            case 3 -> Util.tryGetNextNumberArg(args.get(2));
            default -> List.of();
        };
    }

    @Override
    public String name() {
        return "warehouse";
    }

    @Override
    public String permission() {
        return "malts.command.warehouse";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }


    @Getter
    enum ArgOption {

        DEPOSIT((player, maltsPlayer, warehouse, material, amount) -> {
            if (warehouse.hasCompartment(material)) {
                warehouse.stockWithInventory(player, player.getInventory(), material, amount);
            } else {
                LANG.entry(l -> l.warehouse().compartmentDoesNotExist(), player, Couple.of("{material}", Util.formatEnumerator(material)));
            }
        }),
        WITHDRAW((player, maltsPlayer, warehouse, material, amount) -> {
            if (warehouse.hasCompartment(material)) {
                warehouse.destockToInventory(player, player.getInventory(), material, amount);
            } else {
                LANG.entry(l -> l.warehouse().compartmentDoesNotExist(), player, Couple.of("{material}", Util.formatEnumerator(material)));
            }
        });


        private final Handler executor;

        ArgOption(Handler executor) {
            this.executor = executor;
        }

        private interface Handler {
            void handle(Player sender, MaltsPlayer maltsPlayer, Warehouse warehouse, Material material, int amount);
        }
    }
}
