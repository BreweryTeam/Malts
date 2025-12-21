package dev.jsinco.malts.integration.external.papi;

import dev.jsinco.malts.registry.RegistryItem;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface Placeholder extends RegistryItem {



    String request(@Nullable OfflinePlayer offlinePlayer, List<String> args);


}
