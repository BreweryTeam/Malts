package dev.jsinco.malts.commands.subcommands;

import dev.jsinco.malts.commands.interfaces.SubCommand;
import dev.jsinco.malts.enums.TriState;
import dev.jsinco.malts.gui.WarehouseGui;
import dev.jsinco.malts.model.MaltsPlayer;
import dev.jsinco.malts.model.Stock;
import dev.jsinco.malts.model.Warehouse;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.utility.Couple;
import dev.jsinco.malts.utility.Util;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class WarehouseAdminCommand implements SubCommand {

    private static final List<String> ITEM_MATERIAL_NAMES = Arrays.stream(Material.values())
            .filter(Material::isItem)
            .map(material -> material.name().toLowerCase())
            .toList();

    @Override
    public boolean execute(CommandSender sender, String label, List<String> args) {
        if (args.isEmpty()) return false;
        OfflinePlayer target = Bukkit.getOfflinePlayer(args.getFirst());

        ArgOption option = null;
        if (args.size() > 1) {
            option = Util.getEnum(args.get(1), ArgOption.class);
            if (option == null) return false;
        } else if (!(sender instanceof Player)) {
            // Opening the GUI requires a player
            LANG.entry(l -> l.command().base().playerOnly(), sender);
            return true;
        }

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            LANG.entry(l -> l.vaults().playerNeverOnServer(), sender, Couple.of("{name}", args.getFirst()));
            return true;
        }

        DataSource dataSource = DataSource.getInstance();
        CompletableFuture<MaltsPlayer> playerFuture = dataSource.cacheObjectWithDefaultExpire(dataSource.getMaltsPlayer(target.getUniqueId()));
        CompletableFuture<Warehouse> warehouseFuture = dataSource.cacheObjectWithDefaultExpire(dataSource.getWarehouse(target.getUniqueId()));

        ArgOption finalOption = option;
        List<String> newArgs = args.subList(Math.min(2, args.size()), args.size());
        CompletableFuture.allOf(playerFuture, warehouseFuture).thenRunAsync(() -> {
            MaltsPlayer maltsPlayer = playerFuture.join();
            Warehouse warehouse = warehouseFuture.join();

            if (finalOption == null) {
                WarehouseGui warehouseGui = new WarehouseGui(warehouse, maltsPlayer);
                warehouseGui.open((Player) sender);
                return;
            }

            if (!finalOption.handle(sender, target, maltsPlayer, warehouse, newArgs)) {
                LANG.entry(l -> l.command().base().invalidUsage(), sender);
            }
        });
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, List<String> args) {
        return switch (args.size()) {
            case 1 -> null;
            case 2 -> Stream.of(ArgOption.values()).map(it -> it.toString().toLowerCase()).toList();
            default -> {
                ArgOption option = Util.getEnum(args.get(1), ArgOption.class);
                if (option == null) yield List.of();
                Warehouse warehouse = DataSource.getInstance().cachedObject(Bukkit.getOfflinePlayer(args.getFirst()).getUniqueId(), Warehouse.class);
                yield option.tabComplete(warehouse, args.subList(2, args.size()));
            }
        };
    }

    @Override
    public String permission() {
        return "malts.command.warehouseadmin";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public String name() {
        return "warehouseadmin";
    }


    enum ArgOption {
        GET {
            @Override
            public boolean handle(CommandSender sender, OfflinePlayer target, MaltsPlayer maltsPlayer, Warehouse warehouse, List<String> args) {
                if (args.isEmpty()) return false;
                Material material = evaluateMaterial(sender, args.getFirst());
                if (material == null) return true;

                Couple<String, Object> name = Couple.of("{name}", target.getName());
                Couple<String, Object> materialName = Couple.of("{material}", Util.formatEnumerator(material));
                if (!warehouse.hasCompartment(material)) {
                    LANG.entry(l -> l.command().warehouseAdmin().compartmentDoesNotExist(), sender, name, materialName);
                    return true;
                }
                LANG.entry(l -> l.command().warehouseAdmin().stockGet(), sender, name, materialName,
                        Couple.of("{amount}", warehouse.getQuantity(material)),
                        Couple.of("{stock}", warehouse.currentStockQuantity()),
                        Couple.of("{maxStock}", maltsPlayer.getCalculatedMaxWarehouseStock())
                );
                return true;
            }

            @Override
            public List<String> tabComplete(Warehouse warehouse, List<String> args) {
                return args.size() == 1 ? storedMaterialNames(warehouse) : List.of();
            }
        },
        LIST {
            @Override
            public boolean handle(CommandSender sender, OfflinePlayer target, MaltsPlayer maltsPlayer, Warehouse warehouse, List<String> args) {
                List<Stock> stocks = warehouse.stock().values().stream()
                        .sorted(Comparator.comparing(it -> it.getMaterial().name()))
                        .toList();
                if (stocks.isEmpty()) {
                    LANG.entry(l -> l.command().warehouseAdmin().listEmpty(), sender, Couple.of("{name}", target.getName()));
                    return true;
                }

                LANG.entry(l -> l.command().warehouseAdmin().listHeader(), sender,
                        Couple.of("{name}", target.getName()),
                        Couple.of("{stock}", warehouse.currentStockQuantity()),
                        Couple.of("{maxStock}", maltsPlayer.getCalculatedMaxWarehouseStock())
                );
                for (Stock stock : stocks) {
                    sender.sendMessage(LANG.entry(l -> l.command().warehouseAdmin().listEntry(), "",
                            Couple.of("{material}", Util.formatEnumerator(stock.getMaterial())),
                            Couple.of("{amount}", stock.getAmount())
                    ));
                }
                return true;
            }

            @Override
            public List<String> tabComplete(Warehouse warehouse, List<String> args) {
                return List.of();
            }
        },
        ADD {
            @Override
            public boolean handle(CommandSender sender, OfflinePlayer target, MaltsPlayer maltsPlayer, Warehouse warehouse, List<String> args) {
                if (args.isEmpty()) return false;
                Material material = evaluateMaterial(sender, args.getFirst());
                if (material == null) return true;

                if (warehouse.forceAddCompartment(sender, material)) {
                    warehouse.save(DataSource.getInstance());
                    LANG.entry(l -> l.command().warehouseAdmin().compartmentAdded(), sender,
                            Couple.of("{name}", target.getName()),
                            Couple.of("{material}", Util.formatEnumerator(material))
                    );
                } else {
                    LANG.entry(l -> l.command().warehouseAdmin().compartmentAlreadyExists(), sender,
                            Couple.of("{name}", target.getName()),
                            Couple.of("{material}", Util.formatEnumerator(material))
                    );
                }
                return true;
            }

            @Override
            public List<String> tabComplete(Warehouse warehouse, List<String> args) {
                return args.size() == 1 ? ITEM_MATERIAL_NAMES : List.of();
            }
        },
        REMOVE {
            @Override
            public boolean handle(CommandSender sender, OfflinePlayer target, MaltsPlayer maltsPlayer, Warehouse warehouse, List<String> args) {
                if (args.isEmpty()) return false;
                Material material = evaluateMaterial(sender, args.getFirst());
                if (material == null) return true;

                Couple<String, Object> name = Couple.of("{name}", target.getName());
                Couple<String, Object> materialName = Couple.of("{material}", Util.formatEnumerator(material));
                int amount = warehouse.getQuantity(material);
                TriState result = warehouse.removeCompartment(sender, material);
                switch (result) {
                    case TRUE -> {
                        warehouse.save(DataSource.getInstance());
                        LANG.entry(l -> l.command().warehouseAdmin().compartmentRemoved(), sender, name, materialName);
                    }
                    case FALSE -> LANG.entry(l -> l.command().warehouseAdmin().cannotRemoveCompartment(), sender,
                            name, materialName,
                            Couple.of("{amount}", amount),
                            Couple.of("{materialKey}", material.name().toLowerCase())
                    );
                    case ALTERNATIVE_STATE -> LANG.entry(l -> l.command().warehouseAdmin().compartmentDoesNotExist(), sender, name, materialName);
                }
                return true;
            }

            @Override
            public List<String> tabComplete(Warehouse warehouse, List<String> args) {
                return args.size() == 1 ? storedMaterialNames(warehouse) : List.of();
            }
        },
        SET {
            @Override
            public boolean handle(CommandSender sender, OfflinePlayer target, MaltsPlayer maltsPlayer, Warehouse warehouse, List<String> args) {
                if (args.size() < 2) return false;
                Material material = evaluateMaterial(sender, args.getFirst());
                if (material == null) return true;
                int amount = Util.getInteger(args.get(1), -1);
                if (amount < 0) return false;

                int oldAmount = warehouse.forceSetStock(sender, material, amount);
                warehouse.save(DataSource.getInstance());
                LANG.entry(l -> l.command().warehouseAdmin().stockSet(), sender,
                        Couple.of("{name}", target.getName()),
                        Couple.of("{material}", Util.formatEnumerator(material)),
                        Couple.of("{oldAmount}", oldAmount),
                        Couple.of("{newAmount}", amount),
                        Couple.of("{stock}", warehouse.currentStockQuantity()),
                        Couple.of("{maxStock}", maltsPlayer.getCalculatedMaxWarehouseStock())
                );
                return true;
            }

            @Override
            public List<String> tabComplete(Warehouse warehouse, List<String> args) {
                return switch (args.size()) {
                    case 1 -> warehouse != null && !warehouse.storedMaterials().isEmpty() ? storedMaterialNames(warehouse) : ITEM_MATERIAL_NAMES;
                    case 2 -> Util.tryGetNextNumberArg(args.get(1));
                    default -> List.of();
                };
            }
        },
        TRANSFER {
            @Override
            public boolean handle(CommandSender sender, OfflinePlayer target, MaltsPlayer maltsPlayer, Warehouse warehouse, List<String> args) {
                if (args.size() < 2) return false;
                OfflinePlayer recipient = Bukkit.getOfflinePlayer(args.getFirst());
                if (!recipient.hasPlayedBefore() && !recipient.isOnline()) {
                    LANG.entry(l -> l.vaults().playerNeverOnServer(), sender, Couple.of("{name}", args.getFirst()));
                    return true;
                }
                if (recipient.getUniqueId().equals(target.getUniqueId())) {
                    LANG.entry(l -> l.command().warehouseAdmin().cannotTransferSamePlayer(), sender);
                    return true;
                }

                boolean allMaterials = args.get(1).equalsIgnoreCase(ALL);
                String amountArg = args.size() > 2 ? args.get(2) : ALL;
                boolean allAmount = amountArg.equalsIgnoreCase(ALL);
                int requested = allAmount ? Integer.MAX_VALUE : Util.getInteger(amountArg, -1);
                if (requested <= 0 || (allMaterials && !allAmount)) return false;

                Material material = null;
                if (!allMaterials) {
                    material = evaluateMaterial(sender, args.get(1));
                    if (material == null) return true;
                }

                DataSource dataSource = DataSource.getInstance();
                MaltsPlayer recipientPlayer = dataSource.cacheObjectWithDefaultExpire(dataSource.getMaltsPlayer(recipient.getUniqueId())).join();
                Warehouse recipientWarehouse = dataSource.cacheObjectWithDefaultExpire(dataSource.getWarehouse(recipient.getUniqueId())).join();

                Couple<String, Object> name = Couple.of("{name}", target.getName());
                Couple<String, Object> otherName = Couple.of("{otherName}", recipient.getName());

                if (allMaterials) {
                    int total = 0;
                    int compartments = 0;
                    for (Map.Entry<Material, Integer> entry : warehouse.stockCopy().entrySet()) {
                        if (entry.getValue() <= 0) continue;
                        total += move(sender, warehouse, recipientWarehouse, entry.getKey(), entry.getValue());
                        compartments++;
                    }
                    if (total == 0) {
                        LANG.entry(l -> l.command().warehouseAdmin().nothingToTransferAll(), sender, name);
                        return true;
                    }
                    warehouse.save(dataSource);
                    recipientWarehouse.save(dataSource);
                    LANG.entry(l -> l.command().warehouseAdmin().transferredAll(), sender, name, otherName,
                            Couple.of("{amount}", total),
                            Couple.of("{compartments}", compartments),
                            Couple.of("{otherStock}", recipientWarehouse.currentStockQuantity()),
                            Couple.of("{otherMaxStock}", recipientPlayer.getCalculatedMaxWarehouseStock())
                    );
                    return true;
                }

                Couple<String, Object> materialName = Couple.of("{material}", Util.formatEnumerator(material));
                int available = Math.max(0, warehouse.getQuantity(material));
                if (available == 0) {
                    LANG.entry(l -> l.command().warehouseAdmin().nothingToTransfer(), sender, name, materialName);
                    return true;
                }
                if (!allAmount && requested > available) {
                    LANG.entry(l -> l.command().warehouseAdmin().notEnoughToTransfer(), sender, name, materialName,
                            Couple.of("{available}", available));
                    return true;
                }

                int moved = move(sender, warehouse, recipientWarehouse, material, Math.min(requested, available));
                warehouse.save(dataSource);
                recipientWarehouse.save(dataSource);
                LANG.entry(l -> l.command().warehouseAdmin().transferred(), sender, name, otherName, materialName,
                        Couple.of("{amount}", moved),
                        Couple.of("{otherStock}", recipientWarehouse.currentStockQuantity()),
                        Couple.of("{otherMaxStock}", recipientPlayer.getCalculatedMaxWarehouseStock())
                );
                return true;
            }

            private int move(CommandSender sender, Warehouse from, Warehouse to, Material material, int amount) {
                int recipientAmount = Math.max(0, to.getQuantity(material));
                amount = Math.min(amount, Integer.MAX_VALUE - recipientAmount);
                from.forceSetStock(sender, material, from.getQuantity(material) - amount);
                to.forceSetStock(sender, material, recipientAmount + amount);
                return amount;
            }

            @Override
            public List<String> tabComplete(Warehouse warehouse, List<String> args) {
                return switch (args.size()) {
                    case 1 -> null;
                    case 2 -> Stream.concat(Stream.of(ALL), storedMaterialNames(warehouse).stream()).toList();
                    case 3 -> args.get(1).equalsIgnoreCase(ALL) ? List.of(ALL)
                            : Stream.concat(Stream.of(ALL), Util.tryGetNextNumberArg(args.get(2)).stream()).toList();
                    default -> List.of();
                };
            }
        };

        private static final String ALL = "all";


        public abstract boolean handle(CommandSender sender, OfflinePlayer target, MaltsPlayer maltsPlayer, Warehouse warehouse, List<String> args);

        public abstract List<String> tabComplete(Warehouse warehouse, List<String> args);


        private static Material evaluateMaterial(CommandSender sender, String materialArg) {
            Material material = Util.getEnum(materialArg, Material.class);
            if (material == null || !material.isItem()) {
                LANG.entry(l -> l.command().warehouseAdmin().invalidMaterial(), sender, Couple.of("{material}", materialArg));
                return null;
            }
            return material;
        }

        private static List<String> storedMaterialNames(Warehouse warehouse) {
            if (warehouse == null) return List.of();
            return warehouse.storedMaterials().stream()
                    .map(it -> it.toString().toLowerCase())
                    .toList();
        }
    }
}
