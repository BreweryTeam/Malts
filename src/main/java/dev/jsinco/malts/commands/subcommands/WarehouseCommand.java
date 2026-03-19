package dev.jsinco.malts.commands.subcommands;

import com.google.common.base.Preconditions;
import dev.jsinco.malts.commands.interfaces.SubCommand;
import dev.jsinco.malts.enums.WarehouseMode;
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

        Preconditions.checkNotNull(warehouse, "Warehouse cannot be null for command execution");
        Preconditions.checkNotNull(maltsPlayer, "MaltsPlayer cannot be null for command execution");

        if (args.isEmpty()) {
            WarehouseGui warehouseGui = new WarehouseGui(warehouse, maltsPlayer);
            warehouseGui.open(player);
            return true;
        }

        ArgOption option = Util.getEnum(args.getFirst(), ArgOption.class);

        if (option == null) {
            return false;
        }

        List<String> newArgs = args.subList(1, args.size());

        return option.handle(player, maltsPlayer, warehouse, newArgs);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, List<String> args) {
        Player player = (Player) sender;
        DataSource dataSource = DataSource.getInstance();
        Warehouse warehouse = dataSource.cachedObject(player.getUniqueId(), Warehouse.class);
        Preconditions.checkNotNull(warehouse, "Warehouse cannot be null for tab completion");

        if (args.size() < 2) {
            return Arrays.stream(ArgOption.values())
                    .map(Enum::toString)
                    .map(String::toLowerCase)
                    .toList();
        }

        ArgOption option = Util.getEnum(args.getFirst(), ArgOption.class);
        if (option != null) {
            List<String> newArgs = args.subList(1, args.size());
            return option.tabComplete(player, warehouse, newArgs);
        }
        return List.of();
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


    @SuppressWarnings("Duplicates") // TODO: extract out common code
    @Getter
    enum ArgOption {


        DEPOSIT {
            @Override
            public boolean handle(Player player, MaltsPlayer maltsPlayer, Warehouse warehouse, List<String> args) {
                if (args.size() < 2) return false;
                Material material = Util.getEnum(args.getFirst(), Material.class);
                int amount = Util.getInteger(args.get(1), 0);

                if (!evaluateMaterial(player, material, args.getFirst(), amount)) {
                    return false;
                }

                if (warehouse.hasCompartment(material)) {
                    warehouse.stockWithInventory(player, player.getInventory(), material, amount);
                } else {
                    LANG.entry(l -> l.warehouse().compartmentDoesNotExist(), player, Couple.of("{material}", Util.formatEnumerator(material)));
                }
                return true;
            }

            @Override
            public List<String> tabComplete(Player player, Warehouse warehouse, List<String> args) {
                return switch (args.size()) {
                    case 1 -> warehouse.storedMaterials().stream()
                            .map(it -> it.toString().toLowerCase())
                            .toList();
                    case 2 -> Util.tryGetNextNumberArg(args.get(1));
                    default -> List.of();
                };
            }
        },
        WITHDRAW {
            @Override
            public boolean handle(Player player, MaltsPlayer maltsPlayer, Warehouse warehouse, List<String> args) {
                if (args.size() < 2) return false;
                Material material = Util.getEnum(args.getFirst(), Material.class);
                int amount = Util.getInteger(args.get(1), 0);

                if (!evaluateMaterial(player, material, args.getFirst(), amount)) {
                    return false;
                }

                if (warehouse.hasCompartment(material)) {
                    warehouse.destockToInventory(player, player.getInventory(), material, amount);
                } else {
                    LANG.entry(l -> l.warehouse().compartmentDoesNotExist(), player, Couple.of("{material}", Util.formatEnumerator(material)));
                }
                return true;
            }

            @Override
            public List<String> tabComplete(Player player, Warehouse warehouse, List<String> args) {
                return switch (args.size()) {
                    case 1 -> warehouse.storedMaterials().stream()
                            .map(it -> it.toString().toLowerCase())
                            .toList();
                    case 2 -> Util.tryGetNextNumberArg(args.get(1));
                    default -> List.of();
                };
            }
        },
        MODE {
            @Override
            public boolean handle(Player player, MaltsPlayer maltsPlayer, Warehouse warehouse, List<String> args) {
                WarehouseMode mode = Util.getEnum(args.getFirst(), WarehouseMode.class);
                if (mode == null) {
                    return false;
                }

                if (mode.canUseMode(player)) {
                    LANG.entry(l -> l.warehouse().changedMode(), player, Couple.of("{mode}", Util.formatEnumerator(mode)));
                    maltsPlayer.setWarehouseMode(mode);
                } else {
                    // TODO: custom lang entry
                    LANG.entry(l -> l.command().base().noPermission(), player);
                }
                return true;
            }

            @Override
            public List<String> tabComplete(Player player, Warehouse warehouse, List<String> args) {
                return Arrays.stream(WarehouseMode.values())
                        .filter(mode -> mode.canUseMode(player))
                        .map(it -> it.toString().toLowerCase())
                        .toList();
            }
        };


        public abstract boolean handle(Player player, MaltsPlayer maltsPlayer, Warehouse warehouse, List<String> args);

        public abstract List<String> tabComplete(Player player, Warehouse warehouse, List<String> args);


        private static boolean evaluateMaterial(Player player, Material material, String materialArg, int amount) {
            if (material == null || !material.isItem()) {
                LANG.entry(l -> l.warehouse().blacklistedItem(), player, Couple.of("{material}", materialArg));
                return false;
            } else if (amount <= 0) {
                LANG.entry(l -> l.warehouse().notEnoughMaterial(), player, Couple.of("{material}", Util.formatEnumerator(material)));
                return false;
            }
            return true;
        }

    }
}
