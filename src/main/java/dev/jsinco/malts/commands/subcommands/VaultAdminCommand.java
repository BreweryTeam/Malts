package dev.jsinco.malts.commands.subcommands;

import dev.jsinco.malts.commands.interfaces.SubCommand;
import dev.jsinco.malts.model.MaltsPlayer;
import dev.jsinco.malts.model.Vault;
import dev.jsinco.malts.registry.Registry;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.utility.Couple;
import dev.jsinco.malts.utility.Executors;
import dev.jsinco.malts.utility.Util;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class VaultAdminCommand implements SubCommand {

    private static final List<String> ICON_MATERIAL_NAMES = Arrays.stream(Material.values())
            .filter(Material::isItem)
            .map(material -> material.name().toLowerCase())
            .toList();

    @Override
    public boolean execute(CommandSender sender, String label, List<String> args) {
        if (args.size() < 2) {
            return false;
        }

        ArgOption option = Util.getEnum(args.getFirst(), ArgOption.class);
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(args.get(1));

        if (option == null) {
            return false;
        }

        List<String> newArgs = args.subList(2, args.size());
        return option.getExecutor().handle(sender, label, newArgs, offlinePlayer);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, List<String> args) {
        return switch (args.size()) {
            case 1 -> Stream.of(ArgOption.values()).map(it -> it.toString().toLowerCase()).toList();
            case 3 -> {
                ArgOption option = Util.getEnum(args.getFirst(), ArgOption.class);
                if (option == null) yield null;
                String playerName = args.get(1);
                if (playerName.isEmpty()) yield null;
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
                List<String> newArgs = args.subList(2, args.size());
                yield option.getTabCompleter().handle(sender, label, newArgs, offlinePlayer);
            }
            default -> {
                if (args.size() < 4) yield null;
                ArgOption option = Util.getEnum(args.getFirst(), ArgOption.class);
                if (option != ArgOption.EDIT) yield null;
                String playerName = args.get(1);
                if (playerName.isEmpty()) yield null;
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
                List<String> newArgs = args.subList(2, args.size());
                yield option.getTabCompleter().handle(sender, label, newArgs, offlinePlayer);
            }
        };
    }

    @Override
    public String permission() {
        return "malts.command.vaultadmin";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public String name() {
        return "vaultadmin";
    }

    @Getter
    enum ArgOption {
        DELETE((sender, label, args, offlinePlayer) -> {
            DataSource dataSource = DataSource.getInstance();
            int vaultId = Util.getInteger(args.getFirst(), -1);
            if (vaultId < 0) return false;
            dataSource.deleteVault(offlinePlayer.getUniqueId(), vaultId).thenAccept(deleted -> {
                LANG.entry(l -> {
                    if (deleted) return l.vaults().vaultDeleted();
                    else return l.vaults().noVaultFound();
                }, sender, Couple.of("{id}", vaultId));
            });
            return true;
        }, (sender, label, args, offlinePlayer) -> {
            DataSource dataSource = DataSource.getInstance();
            MaltsPlayer maltsPlayer = dataSource.cachedObject(offlinePlayer.getUniqueId(), MaltsPlayer.class);
            if (maltsPlayer == null ) {
                if(!args.isEmpty()) return Util.tryGetNextNumberArg(args.getFirst());
                else return null;
            }
            return IntStream.rangeClosed(1, maltsPlayer.getCalculatedMaxVaults())
                    .mapToObj(String::valueOf)
                    .collect(Collectors.toList());
        }),
        OPEN((sender, label, args, offlinePlayer) -> {
            VaultOtherCommand vaultOtherCommand = Registry.SUB_COMMANDS.get(VaultOtherCommand.class);
            if (vaultOtherCommand.playerOnly() && !(sender instanceof Player)) return false;
            return vaultOtherCommand.execute(sender, label, Util.plusFirstIndex(args, offlinePlayer.getName()));
        }, (sender, label, args, offlinePlayer) -> {
            VaultOtherCommand vaultOtherCommand = Registry.SUB_COMMANDS.get(VaultOtherCommand.class);
            return vaultOtherCommand.tabComplete(sender, label, Util.plusFirstIndex(args, offlinePlayer.getName()));
        }),
        TRANSFER((sender, label, args, offlinePlayer) -> {
            DataSource dataSource = DataSource.getInstance();
            OfflinePlayer otherPlayer = Bukkit.getOfflinePlayer(args.getFirst());
            if (offlinePlayer.getUniqueId().equals(otherPlayer.getUniqueId())) {
                LANG.entry(l -> LANG.vaults().cannotTransfer(), sender);
                return true;
            }

            Executors.runAsync(task -> {
                List<Vault> player1Vaults = dataSource.getVaults(offlinePlayer.getUniqueId()).join().stream().map(it -> it.toVault().join()).toList();
                List<Vault> player2Vaults = dataSource.getVaults(otherPlayer.getUniqueId()).join().stream().map(it -> it.toVault().join()).toList();

                dataSource.deleteVaults(offlinePlayer.getUniqueId()).join();
                dataSource.deleteVaults(otherPlayer.getUniqueId()).join();
                for (Vault vault : player1Vaults) {
                    dataSource.saveVault(vault.copy(otherPlayer.getUniqueId())).join();
                }
                for (Vault vault : player2Vaults) {
                    dataSource.saveVault(vault.copy(offlinePlayer.getUniqueId())).join();
                }
                LANG.entry(
                        l -> l.vaults().transferred(),
                        sender,
                        Couple.of("{amount}", player1Vaults.size()),
                        Couple.of("{name}", offlinePlayer.getName()),
                        Couple.of("{otherAmount}", player2Vaults.size()),
                        Couple.of("{otherName}", otherPlayer.getName())
                );
            });
            return true;
        }, (sender, label, args, offlinePlayer) -> {
            if (args.size() == 1) {
                return null;
            }
            return List.of();
        }),
        SEARCH((sender, label, args, offlinePlayer) -> {
            SearchCommand searchCommand = Registry.SUB_COMMANDS.get(SearchCommand.class);
            if (searchCommand.playerOnly() && !(sender instanceof Player)) return false;

            List<String>  newArgs = Util.plusFirstIndex(args, "-player", offlinePlayer.getName());
            return searchCommand.execute(sender, label, newArgs);
        }, (sender, label, args, offlinePlayer) -> {
            SearchCommand searchCommand = Registry.SUB_COMMANDS.get(SearchCommand.class);

            return searchCommand.tabComplete(sender, label, Util.plusFirstIndex(args, "-player", offlinePlayer.getName()));
        }),
        EDIT((sender, label, args, offlinePlayer) -> {
            if (args.size() < 2) return false;

            DataSource dataSource = DataSource.getInstance();
            int vaultId = Util.getInteger(args.getFirst(), -1);
            if (vaultId <= 0) return false;

            EditOption editOption = Util.getEnum(args.get(1), EditOption.class);
            if (editOption == null) return false;

            List<String> newArgs = args.subList(2, args.size());
            dataSource.getVault(offlinePlayer.getUniqueId(), vaultId, false).thenAccept(vault -> {
                if (vault == null) {
                    LANG.entry(l -> l.vaults().noVaultFound(), sender);
                    return;
                }
                boolean result = editOption.getExecutor().handle(dataSource, sender, vault, newArgs);
                if (!result) {
                    LANG.entry(l -> l.command().base().invalidUsage(), sender);
                }
            });
            return true;
        }, (sender, label, args, offlinePlayer) -> switch (args.size()) {
            case 1 -> {
                MaltsPlayer maltsPlayer = DataSource.getInstance().cachedObject(offlinePlayer.getUniqueId(), MaltsPlayer.class);
                if (maltsPlayer == null) yield Util.tryGetNextNumberArg(args.getFirst());
                yield IntStream.rangeClosed(1, maltsPlayer.getCalculatedMaxVaults())
                        .mapToObj(String::valueOf)
                        .collect(Collectors.toList());
            }
            case 2 -> Stream.of(EditOption.values()).map(it -> it.toString().toLowerCase()).toList();
            case 3 -> {
                EditOption editOption = Util.getEnum(args.get(1), EditOption.class);
                if (editOption == null) yield List.of();
                yield editOption.getTabCompleter().handle(offlinePlayer.getUniqueId());
            }
            default -> List.of();
        })
        ;

        private final Handler executor;
        private final TabCompleter tabCompleter;

        ArgOption(Handler executor, TabCompleter tabCompleter) {
            this.executor = executor;
            this.tabCompleter = tabCompleter;
        }

        private interface Handler {
            boolean handle(CommandSender sender, String label, List<String> args, OfflinePlayer offlinePlayer);
        }

        private interface TabCompleter {
            List<String> handle(CommandSender sender, String label, List<String> args, OfflinePlayer offlinePlayer);
        }
    }

    @Getter
    @AllArgsConstructor
    private enum EditOption {
        NAME((dataSource, sender, vault, args) -> {
            if (args.isEmpty()) return false;
            String newName = String.join(" ", args);
            vault.forceSetCustomName(newName);
            LANG.entry(l -> l.vaults().nameChanged(), sender, Couple.of("{vaultName}", newName));
            dataSource.saveVault(vault);
            return true;
        }, ownerUuid -> List.of()),
        ICON((dataSource, sender, vault, args) -> {
            if (args.isEmpty()) return false;
            Material material = Util.getEnum(args.getFirst(), Material.class);
            if (material == null || !material.isItem()) return false;
            vault.forceSetIcon(material);
            dataSource.saveVault(vault);
            LANG.entry(l -> l.vaults().iconChanged(), sender, Couple.of("{material}", Util.formatEnumerator(material)));
            return true;
        }, ownerUuid -> ICON_MATERIAL_NAMES),
        TRUST((dataSource, sender, vault, args) -> {
            if (args.isEmpty()) return false;
            String name = args.getFirst();
            OfflinePlayer target = Bukkit.getOfflinePlayer(name);
            UUID targetUUID = target.getUniqueId();
            if (!target.hasPlayedBefore()) {
                LANG.entry(l -> l.vaults().playerNeverOnServer(), sender, Couple.of("{name}", name));
                return true;
            }
            if (vault.getTrustedPlayers().contains(targetUUID)) {
                vault.getTrustedPlayers().remove(targetUUID);
                LANG.entry(l -> l.vaults().playerUntrusted(), sender, Couple.of("{name}", name));
            } else {
                vault.getTrustedPlayers().add(targetUUID);
                LANG.entry(l -> l.vaults().playerTrusted(), sender, Couple.of("{name}", name));
            }
            dataSource.saveVault(vault);
            return true;
        }, ownerUuid -> Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));

        private final EditHandler executor;
        private final EditTabCompleter tabCompleter;

        private interface EditHandler {
            boolean handle(DataSource dataSource, CommandSender sender, Vault vault, List<String> args);
        }

        private interface EditTabCompleter {
            List<String> handle(UUID ownerUuid);
        }
    }
}
