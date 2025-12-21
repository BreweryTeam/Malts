package dev.jsinco.malts.integration.external.papi;

import dev.jsinco.malts.storage.DataSource;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class VaultsPlaceholder implements Placeholder {
    @Override
    public String request(@Nullable OfflinePlayer offlinePlayer, List<String> args) {
        if (offlinePlayer == null) {
            return null;
        }

        String returnValue = "...";

        UUID uuid = offlinePlayer.getUniqueId();
        DataSource dataSource = DataSource.getInstance();

        CachedVaultsAmount cachedValue = dataSource.cachedObject(uuid, CachedVaultsAmount.class);
        if (cachedValue != null) {
            returnValue = cachedValue.getValueAsString();
        }

        if (cachedValue == null || cachedValue.isAboutToExpire()) {
            dataSource.cacheObject(dataSource.getVaults(uuid).thenApply(vaults ->
                    new CachedVaultsAmount(uuid, vaults.size())));
        }

        return returnValue;
    }

    @Override
    public String name() {
        return "vaults";
    }


    public static class CachedVaultsAmount extends CachedPlaceholderRequest {
        private final Integer vaultsAmount;

        public CachedVaultsAmount(UUID owner, Integer vaultsAmount) {
            super(owner);
            this.vaultsAmount = vaultsAmount;
        }

        @Override
        public Integer value() {
            return vaultsAmount;
        }
    }
}
