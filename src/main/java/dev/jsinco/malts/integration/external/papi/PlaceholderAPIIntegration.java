package dev.jsinco.malts.integration.external.papi;

import dev.jsinco.malts.Malts;
import dev.jsinco.malts.integration.Integration;
import dev.jsinco.malts.registry.Registry;
import dev.jsinco.malts.utility.ClassUtil;
import io.papermc.paper.plugin.configuration.PluginMeta;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PlaceholderAPIIntegration implements Integration {


    private PlaceholderDelegate delegate;

    @Override
    public boolean canRegister() { // Extra check for this plugin
        return ClassUtil.classExists("me.clip.placeholderapi.expansion.PlaceholderExpansion") && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    @Override
    public void register() {
        PluginMeta pluginMeta = Malts.getInstance().getPluginMeta();
        this.delegate = new PlaceholderDelegate(pluginMeta);
        this.delegate.register();
    }

    @Override
    public void unregister() {
        if (this.delegate != null) {
            this.delegate.unregister();
        }
    }

    @Override
    public String name() {
        return "PlaceholderAPI";
    }


    private static class PlaceholderDelegate extends PlaceholderExpansion {

        private final PluginMeta pluginMeta;

        public PlaceholderDelegate(PluginMeta pluginMeta) {
            this.pluginMeta = pluginMeta;
        }

        @Override
        public @NotNull String getIdentifier() {
            return pluginMeta.getName();
        }

        @Override
        public @NotNull String getAuthor() {
            return pluginMeta.getAuthors().getFirst();
        }

        @Override
        public @NotNull String getVersion() {
            return pluginMeta.getVersion();
        }

        @Override
        public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
            List<String> args = List.of(params.split("_"));
            if (args.isEmpty()) {
                return null;
            }

            Placeholder placeholder = Registry.PAPI_PLACEHOLDERS.get(args.getFirst());

            if (placeholder != null) {
                return placeholder.request(player, args.subList(1, args.size()));
            }
            return null;
        }
    }
}
