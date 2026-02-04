package dev.jsinco.malts.configuration.files;

import dev.jsinco.malts.configuration.OkaeriFile;
import dev.jsinco.malts.configuration.OkaeriFileName;
import dev.jsinco.malts.utility.Couple;
import dev.jsinco.malts.utility.Text;
import dev.jsinco.malts.utility.Util;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


@OkaeriFileName(
        dynamicFileName = true,
        dynamicFileNameKey = "language",
        dynamicFileNameFormat = "translations/%s.yml"
)
@Getter
@Accessors(fluent = true)
public class Lang extends OkaeriFile {

    private String prefix;
    private String updateAvailable;

    private Warehouse warehouse = new Warehouse();
    private Vaults vaults = new Vaults();
    private Gui gui = new Gui();
    private Economy economy = new Economy();
    private Command command = new Command();

    @Getter
    @Accessors(fluent = true)
    public static class Warehouse extends OkaeriConfig {
        private String notEnoughMaterial;
        private String notEnoughStock;
        private String inventoryFull;
        private String containerFull;
        private String removedCompartment;
        private String cannotRemoveCompartment;
        private String addedCompartment;
        private String compartmentAlreadyExists;
        private String compartmentDoesNotExist;
        private String changedMode;
        private String replenishedItem;
        private String autoStoredItem;
        private String depositedItem;
        private String cannotDepositItem;
        private String blacklistedItem;
        private String addedItem;
        private String withdrewItem;
    }

    @Getter
    @Accessors(fluent = true)
    public static class Vaults extends OkaeriConfig {
        private String opening;
        private String nameChanged;
        private String noAccess;
        private String alreadyOpen;
        private String trustListMaxed;
        private String playerNeverOnServer;
        private String playerTrusted;
        private String playerUntrusted;
        private String playerNotTrusted;
        private String noVaultsFound;
        private String noVaultsAccessible;
        private String noVaultFound;
        private String vaultDeleted;
        private String vaultNameTooLong;
        private String transferred;
        private String cannotTransfer;
        private String iconChanged;
    }

    @Getter
    @Accessors(fluent = true)
    public static class Gui extends OkaeriConfig {
        private String firstPage;
        private String lastPage;
        private String viewExpired;
        private String promptInputTimeOut;
    }

    @Getter
    @Accessors(fluent = true)
    public static class Command extends OkaeriConfig {

        private String help;
        private Base base = new Base();
        private Max max = new Max();
        @CustomKey("import")
        private Import _import = new Import();
        private VaultOther vaultOther = new VaultOther();
        private Reload reload = new Reload();
        private QuickReturn quickReturn = new QuickReturn();
        private Search search = new Search();

        @Getter
        @Accessors(fluent = true)
        public static class Base extends OkaeriConfig {
            private String unknownCommand;
            private String playerOnly;
            private String noPermission;
            private String invalidUsage;
        }
        @Getter
        @Accessors(fluent = true)
        public static class Max extends OkaeriConfig {
            private String invalidType;
            private String success;
        }
        @Getter
        @Accessors(fluent = true)
        public static class Import extends OkaeriConfig {
            private String cannotImport;
            private String startImport;
            private String importComplete;
            private String failedImport;
        }
        @Getter
        @Accessors(fluent = true)
        public static class VaultOther extends OkaeriConfig {
            private String noVaultFound;
        }
        @Getter
        @Accessors(fluent = true)
        public static class Reload extends OkaeriConfig {
            private String success;
            private String failed;
            private String newDatabaseDriverSet;
        }

        @Getter
        @Accessors(fluent = true)
        public static class QuickReturn extends OkaeriConfig {
            private String success;
            private String failed;
        }

        @Getter
        @Accessors(fluent = true)
        public static class Search extends OkaeriConfig {
            private String noResults;
            private String resultFormat;
            private String results;
            private String previousPage;
            private String nextPage;
            private String playerNotFound;
            private String noAccessibleVaults;
        }
    }

    @Getter
    @Accessors(fluent = true)
    public static class Economy extends OkaeriConfig {
        private Vaults vaults = new Vaults();

        @Getter
        @Accessors(fluent = true)
        public static class Vaults extends OkaeriConfig {
            private String created;
            private String cannotAffordCreation;
            private String accessed;
            private String cannotAffordAccess;
        }
    }


    @SafeVarargs
    @Nullable
    public final Component entry(@Nullable String entry, boolean prefix, Couple<String, Object>... placeholders) {
        if (entry == null) {
            entry = "<red>Lang entry was not found.";
        } else if (entry.isEmpty()) {
            return null;
        }

        return Text.mm((prefix ? this.prefix : "") + Util.replace(entry, placeholders));
    }

    @NotNull
    public final Component entry(FunctionalLang functionalLang, String defaultValue, Couple<String, Object>... placeholders) {
        String entry = functionalLang.get(this);
        if (entry == null || entry.isEmpty()) {
            entry = defaultValue;
        }

        return Text.mm(Util.replace(entry, placeholders));
    }

    @NotNull
    public final Component entry(@Nullable String entry, String defaultValue, Couple<String, Object>... placeholders) {
        if (entry == null || entry.isEmpty()) {
            entry = defaultValue;
        }

        return Text.mm(Util.replace(entry, placeholders));
    }

    @SafeVarargs
    @Nullable
    public final Component entry(FunctionalLang functionalLang, boolean prefix, Couple<String, Object>... placeholders) {
        String entry = functionalLang.get(this);
        if (entry == null) {
            entry = "<red>Lang entry was not found.";
        } else if (entry.isEmpty()) {
            return null;
        }

        return Text.mm((prefix ? this.prefix : "") + Util.replace(entry, placeholders));
    }

    @SafeVarargs
    @Nullable
    public final Component entry(FunctionalLang functionalLang, Audience receiver, Couple<String, Object>... placeholders) {
        Component comp = this.entry(functionalLang, true, placeholders);
        if (comp != null) {
            receiver.sendMessage(comp);
        }
        return comp;
    }

    @SafeVarargs
    @Nullable
    public final Component entry(FunctionalLang functionalLang, List<Audience> receivers, Couple<String, Object>... placeholders) {
        Component comp = this.entry(functionalLang, true, placeholders);
        if (comp != null) {
            receivers.forEach(receiver -> receiver.sendMessage(comp));
        }
        return comp;
    }

    @SafeVarargs
    @Nullable
    public final Component actionBarEntry(FunctionalLang functionalLang, Audience receiver, Couple<String, Object>... placeholders) {
        Component comp = this.entry(functionalLang, false, placeholders);
        if (comp != null) {
            receiver.sendActionBar(comp);
        }
        return comp;
    }
    
    @Override
    public String name() {
        return "lang.yml";
    }

    public interface FunctionalLang {
        String get(Lang lang);
    }
}
