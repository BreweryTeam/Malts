package dev.jsinco.malts.configuration.files;

import dev.jsinco.malts.configuration.ConfigGuiArrowItem;
import dev.jsinco.malts.configuration.IntPair;
import dev.jsinco.malts.configuration.OkaeriFile;
import dev.jsinco.malts.configuration.OkaeriFileName;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.Material;

import java.util.List;

@Getter
@Accessors(fluent = true)
@SuppressWarnings("FieldMayBeFinal")
@OkaeriFileName("gui.yml")
public class GuiConfig extends OkaeriFile {

    @Comment("Borders are placed in guis when enabled and the present item slot is empty.")
    private BorderItem borderItem = new BorderItem();
    @Comment("'altSlot' is used when the warehouse quickbar is not present.")
    private YourVaultsGui yourVaultsGui = new YourVaultsGui();
    private WarehouseGui warehouseGui = new WarehouseGui();
    private VaultOtherGui vaultOtherGui = new VaultOtherGui();
    private EditVaultGui editVaultGui = new EditVaultGui();


    @Getter
    @Accessors(fluent = true)
    public static class BorderItem extends OkaeriConfig {
        private String name = "";
        private List<String> lore = List.of();
        private Material material = Material.GRAY_STAINED_GLASS_PANE;
    }

    @Getter
    @Accessors(fluent = true)
    public static class EditVaultGui extends OkaeriConfig {
        private String title = "Editing Vault #{id}";
        private int size = 9;
        private boolean borders = false;

        private EditVaultGuiItem backButton = new EditVaultGuiItem(
                "<red>Back",
                List.of(),
                Material.BARRIER,
                0
        );
        private EditVaultGuiItem editNameButton = new EditVaultGuiItem(
                "<aqua>Change Name",
                List.of(
                        "",
                        "<white>Click to change the name",
                        "<white>of this vault.",
                        "<white>- <i>'{vaultName}'"
                ),
                Material.NAME_TAG,
                6
        );
        private EditVaultGuiItem editIconButton = new EditVaultGuiItem(
                "<aqua>Change Icon",
                List.of(
                        "",
                        "<white>Click to change the icon of",
                        "<white>of this vault. Upon clicking,",
                        "<white>select an item in your inventory."
                ),
                Material.CHEST_MINECART,
                7
        );
        private EditVaultGuiTrustListItem editTrustListButton = new EditVaultGuiTrustListItem(
                "<aqua>Edit Trust List <gray>({trustedListSize})",
                List.of(
                        "",
                        "Click to edit the list of",
                        "players who have access to",
                        "this vault.",
                        "",
                        "{trustedList}"
                ),
                Material.PLAYER_HEAD,
                8,
                "{name}"
        );
        private EditVaultGuiToggleItem lockButton = new EditVaultGuiToggleItem(
                "<aqua>Lock Vault <gray>({state})",
                List.of(
                        "",
                        "<white>While locked, taken items leave",
                        "<white>a placeholder behind and can only",
                        "<white>be put back into their own slot.",
                        "",
                        "<white>Click to toggle."
                ),
                Material.CLOSED_EYEBLOSSOM,
                Material.OPEN_EYEBLOSSOM,
                "<red>Locked</red>",
                "<green>Unlocked</green>",
                5
        );

        @Getter
        @Accessors(fluent = true)
        @AllArgsConstructor
        public static class EditVaultGuiItem extends OkaeriConfig {
            private String name;
            private List<String> lore;
            private Material material;
            private int slot;
        }

        @Getter
        @Accessors(fluent = true)
        @AllArgsConstructor
        public static class EditVaultGuiToggleItem extends OkaeriConfig {
            private String name;
            private List<String> lore;
            private Material lockedMaterial;
            private Material unlockedMaterial;
            private String lockedState;
            private String unlockedState;
            private int slot;
        }

        @Getter
        @Accessors(fluent = true)
        @AllArgsConstructor
        public static class EditVaultGuiTrustListItem extends OkaeriConfig {
            private String name;
            private List<String> lore;
            private Material material;
            private int slot;
            private String headOwner;
        }
    }

    @Getter
    @Accessors(fluent = true)
    public static class VaultOtherGui extends OkaeriConfig {
        private String title = "{name}'s Vaults";
        private int size = 54;
        private boolean borders = true;

        private VaultItem vaultItem = new VaultItem();
        private ConfigGuiArrowItem previousPage = new ConfigGuiArrowItem("Previous Page", List.of(), 48, Material.ARROW);
        private ConfigGuiArrowItem nextPage = new ConfigGuiArrowItem("Next Page", List.of(), 50, Material.ARROW);


        @Getter
        @Accessors(fluent = true)
        public static class VaultItem extends OkaeriConfig {
            private String name = "<aqua><b>{vaultName}"; // {id} too
            private List<String> lore = List.of(
                    "",
                    "<white>Left-click to open",
                    "<white>this vault."
            );
            private IntPair slots = IntPair.of(0, 44);
            private List<Integer> ignoredSlots = List.of();
        }

    }

    @Getter
    @Accessors(fluent = true)
    public static class YourVaultsGui extends OkaeriConfig {
        private String title = "Your Vaults";
        private int size = 54;
        private boolean borders = true;

        private VaultItem vaultItem = new VaultItem();
        private WarehouseQuickbar warehouseQuickbar = new WarehouseQuickbar();
        private YourVaultsGuiArrowItem previousPage = new YourVaultsGuiArrowItem("Previous Page", List.of(), 39, 48, Material.ARROW);
        private YourVaultsGuiArrowItem nextPage = new YourVaultsGuiArrowItem("Next Page", List.of(), 41, 50, Material.ARROW);

        @Getter
        @Accessors(fluent = true)
        @AllArgsConstructor
        public static class YourVaultsGuiArrowItem extends OkaeriConfig {
            private String name;
            private List<String> lore;
            private int slot;
            private int altSlot;
            private Material material;
        }

        @Getter
        @Accessors(fluent = true)
        public static class VaultItem extends OkaeriConfig {
            // TODO: glow when full?
            // TODO: % used of vault placeholder
            private String name = "<aqua><b>{vaultName}";
            private List<String> lore = List.of(
                    "",
                    "<white>Left-click to open",
                    "<white>this vault.",
                    "",
                    "<white>Right-click to open",
                    "<white>your vault settings."
            );
            private IntPair slots = IntPair.of(0, 35);
            private IntPair altSlots = IntPair.of(0, 44);
            private List<Integer> ignoredSlots = List.of();
            private List<Integer> altIgnoredSlots = List.of();
        }

        @Getter
        @Accessors(fluent = true)
        public static class WarehouseQuickbar extends OkaeriConfig {
            private String name = "<aqua>View Warehouse";
            private List<String> lore = List.of("", "<white>Click to view your", "<white>warehouse");
            private Material material = Material.ENDER_CHEST;
            private int slot = 45;
            private IntPair slots = IntPair.of(46, 53);
            private List<Integer> ignoredSlots = List.of();
        }
    }

    @Getter
    @Accessors(fluent = true)
    public static class WarehouseGui extends OkaeriConfig {
        private String title = "Your Warehouse";
        private int size = 45;
        private boolean borders = true;

        private WarehouseItem warehouseItem = new WarehouseItem();
        private ManagerButton managerButton = new ManagerButton();
        private StatusIcon statusIcon = new StatusIcon();
        private ConfigGuiArrowItem previousPage = new ConfigGuiArrowItem("Previous Page", List.of(), 39, Material.ARROW);
        private ConfigGuiArrowItem nextPage = new ConfigGuiArrowItem("Next Page", List.of(), 41, Material.ARROW);

        @Getter
        @Accessors(fluent = true)
        public static class WarehouseItem extends OkaeriConfig {
            private String name = "<aqua><b>{material}";
            private List<String> lore = List.of(
                    "",
                    "<gray>+ Quantity: <yellow>{quantity}</yellow>",
                    "",
                    "<gray><dark_gray>[<gold>Left Click</gold>]</dark_gray> Withdraw one item.",
                    "<gray><dark_gray>[<gold>Right Click</gold>]</dark_gray> Withdraw a stack.",
                    "<gray><dark_gray>[<gold>Shift Left</gold>]</dark_gray> Withdraw all.",
                    "<gray><dark_gray>[<gold>Shift Right</gold>]</dark_gray> Add all to storage."
            );
            private IntPair slots = IntPair.of(10, 34);
            private List<Integer> ignoredSlots = List.of(17, 18, 26, 27);
        }

        @Getter
        @Accessors(fluent = true)
        public static class ManagerButton extends OkaeriConfig {
            private String name = "<aqua><b>Manage Warehouse";
            private List<String> lore = List.of(
                    "",
                    "<gray>+ Mode: <yellow>{mode}</yellow>",
                    "",
                    "<gray><dark_gray>[<gold>Left Click</gold>]</dark_gray> Add a compartment.",
                    "<gray><dark_gray>[<gold>Right Click</gold>]</dark_gray> Remove a compartment.",
                    "<gray><dark_gray>[<gold>Shift Left</gold>]</dark_gray> Cycle warehouse mode."
            );
            private Material material = Material.CHEST_MINECART;
            private int slot = 40;
        }

        @Getter
        @Accessors(fluent = true)
        public static class StatusIcon extends OkaeriConfig {
            private String name = "<aqua><b>Warehouse Information";
            private List<String> lore = List.of(
                    "",
                    "<gray>+ Owner: <yellow>{name}</yellow>",
                    "",
                    "<gray>+ Max Stock: <yellow>{maxStock}</yellow>",
                    "",
                    "<gray>+ Space Used: <yellow>{stockPercent}%</yellow>"
            );
            private Material material = Material.PLAYER_HEAD;
            private int slot = 44;
            private String headOwner = "{name}";
        }
    }
}
