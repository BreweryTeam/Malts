package dev.jsinco.malts.configuration.files;

import dev.jsinco.malts.configuration.OkaeriFile;
import dev.jsinco.malts.configuration.OkaeriFileName;
import dev.jsinco.malts.enums.Driver;
import dev.jsinco.malts.enums.EconomyProvider;
import dev.jsinco.malts.enums.QuickReturnClickType;
import dev.jsinco.malts.enums.WarehouseMode;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Getter
@Accessors(fluent = true)
@OkaeriFileName("config.yml")
public class Config extends OkaeriFile {

    @Comment({
            "The language file to use for Malts.",
            "The file must be located in the 'translations' directory.",
            "If you would like to use a language file that is not provided by Malts,",
            "you will need to create it yourself and place it in the 'translations' directory.",
            "* Options: en_us (English, US), zh_cn (Chinese, Simplified)"
    })
    private String language = "en_us";

    @Comment("What subcommand should the base command default to?")
    private String baseCommandBehavior = "vaults";


    @Comment("Toggles debug messages.")
    private boolean verboseLogging = false;

    @Comment("Enables or disables the update checker.")
    private boolean updateChecker = true;


    @Comment({
            "Quick return allows players to click outside of their",
            "vaults or warehouse to return to the main 'Your Vaults'",
            "gui."
    })
    private QuickReturn quickReturn = new QuickReturn();
    @Getter
    @Accessors(fluent = true)
    public static class QuickReturn extends OkaeriConfig {
        @Comment("Whether or not to enable the quick return feature.")
        private boolean enabled = true;
        @Comment({
                "The default click types that will trigger",
                "the quick return feature. Players can configure",
                "their own click type preferences individually.",
                "using '/malts quickreturn <type>'",
                " ",
                "* Options: LEFT, RIGHT, MIDDLE, NONE",
        })
        private QuickReturnClickType defaultClickType = QuickReturnClickType.RIGHT;
    }

    @Comment({
            "These are the Malts storage settings. All tables are prefixed",
            "with 'malts_' on both SQLite and MySQL to ensure uniqueness."
    })
    private Storage storage = new Storage();
    @Getter
    @Accessors(fluent = true)
    public static class Storage extends OkaeriConfig {
        @Comment({
                "How long Malts should keep cacheable objects in",
                "memory before saving them back to disk. The default value",
                "is fine for most servers."
        })
        private long defaultObjectCacheTime = 1200000;
        @Comment({
                "The driver to use for storing data. Malts provides no",
                "methods for swapping storage drivers. If you would like to",
                "change storage methods, you will need to do so manually using",
                "SQL editors or phpMyAdmin for importing & exporting.",
                " ",
                "* Your available options are: SQLITE, MYSQL",
                "If you choose to use MYSQL as your storage method",
                "you must configure your connection details."
        })
        private Driver driver = Driver.SQLITE;

        @Comment("The name of the database. When using SQLite, this becomes the file name.")
        private String database = "malts";

        private String host = "localhost";

        private int port = 3306;

        private String username = "root";

        private String password = "password";

        private String jdbcFlags = "?useSSL=false&verifyServerCertificate=false&useUnicode=true&characterEncoding=utf-8";
    }

    private Vaults vaults = new Vaults();
    @Getter
    @Accessors(fluent = true)
    public static class Vaults extends OkaeriConfig {
        @Comment({
                "This is the default title of all vaults for Malts.",
                "This does not prevent players from changing their vault",
                "names to whatever they want."
        })
        private String defaultName = "Vault #{id}";

        @Comment({
                "The default icon for all vaults. This does not prevent",
                "players from setting their own custom icon."
        })
        private Material defaultIcon = Material.CHEST;

        @Comment({
                "The size for all Malts vaults. This setting applies globally.",
                "If this setting is lowered and players already have vaults that fill up to 54 slots,",
                "Malts will dynamically increase the size of the inventory until the inventory's",
                "items have been lowered to the desired configured amount."
        })
        private int size = 54;
        @Comment({
                "The maximum amount of characters that can be",
                "used to rename a vault."
        })
        private int maxNameCharacters = 25;

        @Comment({
                "The maximum amount of players that can be trusted to a single vault",
                "(not including the owner of the vault). This setting does not apply to",
                "warehouses as warehouses have no concept of 'trusting' and can only be",
                "accessed by the owner and/or moderators who have elevated permissions."
        })
        private int trustCap = 3;

        @Comment({
                "The default amount of vaults players have.",
                "Players may have their vaults increased by using",
                "'malts.maxvaults.<amount>' permissions.",
                "Or, by using '/malts max vaults add <player> <amount>'.",
        })
        private int defaultMaxVaults = 0;
    }


    private Warehouse warehouse = new Warehouse();
    @Getter
    @Accessors(fluent = true)
    public static class Warehouse extends Vaults {

        @Comment({
                "Automatically blacklist items that have a stack <= 1."
        })
        private boolean blacklistSingleStackMaterials = true;
        @Comment({
                "Manually prevent items from being stored in warehouses.",
                "Supports regex."
        })
        private List<String> blacklistedMaterials = List.of();
        public List<Material> blacklistedMaterials() {
            Material[] materials = Material.values();
            List<Pattern> patterns = blacklistedMaterials.stream().map(Pattern::compile).toList();
            List<Material> blacklist = new ArrayList<>();
            for (Material material : materials) {
                if (blacklistedMaterials.contains(material.name())) {
                    blacklist.add(material);
                } else if (patterns.stream().anyMatch(pattern -> pattern.matcher(material.toString()).matches())) {
                    blacklist.add(material);
                }
            }
            return blacklist;
        }

        @Comment({
                "All enabled warehouse modes for Malts.",
                "To use a specific mode, a player must have the",
                "permission 'malts.warehouse.mode.<mode> (e.g. malts.warehouse.mode.auto_store)",
                " ",
                "* AUTO_STORE: If a player has a compartment for the item they are picking up",
                "it will automatically be placed into the player's warehouse",
                "* CLICK_TO_DEPOSIT: Players can shift click any container to deposit items from their warehouse.",
                "the items they're depositing depends on the material they are holding in their hand.",
                "* AUTO_REPLENISH: Automatically replenish blocks and consumables when they're running low with",
                "stock from the player's warehouse."
        })
        private List<WarehouseMode> enabledModes = List.of(
                WarehouseMode.AUTO_STORE,
                WarehouseMode.CLICK_TO_DEPOSIT,
                WarehouseMode.AUTO_REPLENISH
        );


        @Comment({
                "The default amount of warehouse stock players have.",
                "Players may have their stock increased by using",
                "'malts.maxstock.<amount>' permissions.",
                "Or, by using '/malts max stock add <player> <amount>'.",
        })
        private int defaultMaxStock = 0;
    }

    private Economy economy = new Economy();
    @Getter
    @Accessors(fluent = true)
    public static class Economy extends OkaeriConfig {

        @Comment({
                "The economy provider to use for Malts transactions",
                "* Options: NONE, VAULT, PLAYER_POINTS"
        })
        private EconomyProvider economyProvider = EconomyProvider.NONE;
        private Vaults vaults = new Vaults();

        @Getter
        @Accessors(fluent = true)
        public static class Vaults extends OkaeriConfig {
            @Comment({
                    "The amount of currency a player is charged",
                    "to create a new vault. Bypass with 'malts.bypass.economy'",
                    "* Requires 'economyProvider' to be set to a valid provider.",

            })
            private double creationFee = 0.0;

            @Comment({
                    "The amount of currency a player is charged",
                    "to open a vault. Bypass with 'malts.bypass.economy'",
                    "* Requires 'economyProvider' to be set to a valid provider.",
            })
            private double accessFee = 0.0;
        }
    }
}
