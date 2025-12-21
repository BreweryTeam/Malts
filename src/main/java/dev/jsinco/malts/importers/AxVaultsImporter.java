package dev.jsinco.malts.importers;

import dev.jsinco.malts.model.Vault;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.utility.ClassUtil;
import dev.jsinco.malts.utility.Couple;
import dev.jsinco.malts.utility.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AxVaultsImporter implements Importer {

    @Override
    public String name() {
        return "AxVaults";
    }

    @Override
    public boolean canImport() {
        return ClassUtil.classExists("com.artillexstudios.axvaults.AxVaults");
    }

    @Override
    public CompletableFuture<Map<UUID, Result>> importAll() {
        OfflinePlayer[] players = Bukkit.getOfflinePlayers();

        if (players.length == 0) {
            return CompletableFuture.completedFuture(Map.of());
        }

        List<CompletableFuture<Couple<UUID, Result>>> futures = Arrays.stream(players)
                .map(this::importVaults)
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toMap(Couple::a, Couple::b)));
    }


    private CompletableFuture<Couple<UUID, Result>> importVaults(OfflinePlayer offlinePlayer) {
        UUID uuid = offlinePlayer.getUniqueId();

        return com.artillexstudios.axvaults.vaults.VaultManager.getPlayer(offlinePlayer)
                .thenCompose(vaultPlayer -> {
                    Map<Integer, com.artillexstudios.axvaults.vaults.Vault> vaultMap = vaultPlayer.getVaultMap();

                    if (vaultMap.isEmpty()) {
                        return CompletableFuture.completedFuture(Couple.of(uuid, Result.NO_VAULTS_IN_OTHER_PLUGIN));
                    }

                    Map<Integer, ItemStack[]> inventories = vaultMap.entrySet().stream()
                            .filter(entry -> {
                                ItemStack[] contents = entry.getValue().getStorage().getContents();
                                return contents != null && contents.length > 0;
                            })
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> entry.getValue().getStorage().getContents()
                            ));

                    return DataSource.getInstance().getVaults(uuid)
                            .thenCompose(existing -> {
                                if (!existing.isEmpty()) {
                                    return CompletableFuture.completedFuture(Couple.of(uuid, Result.VAULTS_NOT_EMPTY));
                                }

                                return saveAllVaults(uuid, inventories)
                                        .thenApply(v -> Couple.of(uuid, Result.SUCCESS));
                            });
                })
                .exceptionally(error -> {
                    Text.error("Failed to import vaults for " + uuid, error);
                    return Couple.of(uuid, Result.FAILED);
                });
    }

    private CompletableFuture<Void> saveAllVaults(UUID uuid, Map<Integer, ItemStack[]> inventories) {
        DataSource dataSource = DataSource.getInstance();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (var entry : inventories.entrySet()) {
            int index = entry.getKey();
            ItemStack[] inv = entry.getValue();
            chain = chain.thenCompose(v ->
                    dataSource.saveVault(new Vault(uuid, index, inv))
                            .thenRun(() -> Text.log("Imported vault #" + index + " for " + uuid))
            );
        }

        return chain;
    }

}
