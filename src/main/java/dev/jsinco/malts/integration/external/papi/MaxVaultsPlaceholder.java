package dev.jsinco.malts.integration.external.papi;

import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.files.Config;
import dev.jsinco.malts.model.MaltsPlayer;
import dev.jsinco.malts.storage.DataSource;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MaxVaultsPlaceholder implements Placeholder {

    @Override
    public String request(@Nullable OfflinePlayer offlinePlayer, List<String> args) {
        if (offlinePlayer == null || !offlinePlayer.isOnline()) {
            return String.valueOf(ConfigManager.get(Config.class).vaults().defaultMaxVaults());
        }
        DataSource dataSource = DataSource.getInstance();
        MaltsPlayer maltsPlayer = dataSource.cachedObject(offlinePlayer.getUniqueId(), MaltsPlayer.class);
        if (maltsPlayer == null) {
            return String.valueOf(ConfigManager.get(Config.class).vaults().defaultMaxVaults());
        }
        return String.valueOf(maltsPlayer.getCalculatedMaxVaults());
    }

    @Override
    public String name() {
        return "maxvaults";
    }
}
