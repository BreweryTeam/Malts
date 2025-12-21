package dev.jsinco.malts.commands.subcommands;

import dev.jsinco.malts.Malts;
import dev.jsinco.malts.commands.interfaces.SubCommand;
import dev.jsinco.malts.model.CachedObject;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.utility.Couple;
import lombok.Getter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class VaultNameCommand implements SubCommand {

    @Override
    public boolean execute(CommandSender sender, String label, List<String> args) {
        if (args.isEmpty()) {
            return false;
        }

        Player player = (Player) sender;

        String vaultName = args.getFirst();
        DataSource dataSource = DataSource.getInstance();
        dataSource.getVault(player.getUniqueId(), vaultName).thenAccept(vault -> {
            if (vault == null) {
                LANG.entry(l -> l.vaults().noVaultFound(), player);
                return;
            }
            vault.open(player);
            LANG.entry(l -> l.vaults().opening(), player, Couple.of("{vaultName}", vault.getCustomName()));
        });
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, List<String> args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        DataSource dataSource = DataSource.getInstance();
        CachedVaultNames cachedVaultNames = dataSource.cachedObject(player.getUniqueId(), CachedVaultNames.class);

        if (cachedVaultNames == null) { // TODO: This could happen too often and end up trying to cache the same object X amount of times
            CompletableFuture<CachedVaultNames> future = dataSource.getVaultNames(player.getUniqueId())
                            .thenApply(vaultNames -> new CachedVaultNames(player.getUniqueId(), vaultNames));
            // 60s expire time
            dataSource.cacheObject(future, 60000).thenAccept(cached -> {
                // No action needed here for tab completion
            });
            return List.of("...");
        }

        return cachedVaultNames.getVaultNames();
    }

    @Override
    public String permission() {
        return "malts.command.vaultname";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    @Override
    public String name() {
        return "vaultname";
    }

    @Getter
    private static class CachedVaultNames implements CachedObject {

        private final UUID owner;
        private final List<String> vaultNames;
        private Long expire;

        public CachedVaultNames(UUID owner, List<String> vaultNames) {
            this.owner = owner;
            this.vaultNames = vaultNames;
        }

        @Override
        public @NotNull UUID getUuid() {
            return owner;
        }

        @Override
        public @Nullable Long getExpire() {
            return expire;
        }

        @Override
        public void setExpire(@Nullable Long expire) {
            this.expire = expire;
        }

        @Override
        public @NotNull CompletableFuture<Void> save(DataSource dataSource) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
