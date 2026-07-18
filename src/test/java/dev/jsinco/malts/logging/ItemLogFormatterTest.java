package dev.jsinco.malts.logging;

import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemLogFormatterTest {

    @BeforeAll
    static void mock() {
        MockBukkit.mock();
    }

    @AfterAll
    static void unmock() {
        MockBukkit.unmock();
    }

    @Test
    void plainItemRecordsOnlyAmountAndMaterial() {
        String result = ItemLogFormatter.format(new ItemStack(Material.STONE, 5), 5, LogDetail.BASIC, List.of());
        assertTrue(result.contains("5x"), "should record amount");
        assertTrue(result.toLowerCase().contains("stone"), "should record material");
        assertFalse(result.contains("{"), "a plain item must not emit a custom-meta block");
    }

    @Test
    void basicRecordsCustomModelDataAndName() {
        ItemStack item = new ItemStack(Material.DIAMOND, 3);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(42);
        meta.displayName(Component.text("Shiny"));
        item.setItemMeta(meta);

        String result = ItemLogFormatter.format(item, 3, LogDetail.BASIC, List.of());
        assertTrue(result.contains("3x"), "should record amount");
        assertTrue(result.contains("cmd=42"), "should record custom model data");
        assertTrue(result.contains("name=\"Shiny\""), "should record the custom display name");
    }

    @Test
    void basicOmitsLoreAndEnchants() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD, 1);
        ItemMeta meta = item.getItemMeta();
        meta.lore(java.util.List.of(Component.text("secret lore")));
        item.setItemMeta(meta);
        item.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);

        String result = ItemLogFormatter.format(item, 1, LogDetail.BASIC, List.of());
        assertFalse(result.contains("secret lore"), "BASIC must not record lore");
        assertFalse(result.contains("enchants"), "BASIC must not record enchantments");
    }

    @Test
    void detailedRecordsLoreAndEnchants() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD, 1);
        ItemMeta meta = item.getItemMeta();
        meta.lore(java.util.List.of(Component.text("secret lore")));
        item.setItemMeta(meta);
        item.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);

        String result = ItemLogFormatter.format(item, 1, LogDetail.DETAILED, List.of());
        assertTrue(result.contains("secret lore"), "DETAILED should record lore");
        assertTrue(result.contains("sharpness"), "DETAILED should record enchantments");
    }

    @Test
    void recordsConfiguredPersistentDataKeysAndSkipsBlobs() {
        ItemStack item = new ItemStack(Material.DIAMOND, 1);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(
                new NamespacedKey("lumaitems", "itemid"), PersistentDataType.STRING, "magic_sword");
        meta.getPersistentDataContainer().set(
                new NamespacedKey("otherplugin", "blob"), PersistentDataType.BYTE_ARRAY, new byte[]{1, 2, 3, 4});
        item.setItemMeta(meta);

        List<String> keys = List.of("lumaitems:itemid", "otherplugin:blob");
        String result = ItemLogFormatter.format(item, 1, LogDetail.BASIC, keys);

        assertTrue(result.contains("pdc={lumaitems:itemid=magic_sword}"),
                "should record the configured string PDC key inside a pdc={...} group");
        assertFalse(result.contains("otherplugin:blob"), "must skip non-simple (blob) PDC values");
    }

    @Test
    void recordsKeysUnderConfiguredNamespaceWildcard() {
        ItemStack item = new ItemStack(Material.DIAMOND, 1);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(
                new NamespacedKey("lumaitems", "magic_sword"), PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(
                new NamespacedKey("someplugin", "internal"), PersistentDataType.STRING, "x");
        item.setItemMeta(meta);

        String result = ItemLogFormatter.format(item, 1, LogDetail.BASIC, List.of("lumaitems:*"));
        assertTrue(result.contains("pdc={lumaitems:magic_sword="), "should record the id encoded in the key path and its value");
        assertFalse(result.contains("someplugin"), "must not record keys from other namespaces");
    }

    @Test
    void groupsAllPersistentDataKeysIntoASinglePdcBlock() {
        ItemStack item = new ItemStack(Material.DIAMOND, 1);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(
                new NamespacedKey("lumaitems", "itemid"), PersistentDataType.STRING, "magic_sword");
        meta.getPersistentDataContainer().set(
                new NamespacedKey("lumaitems", "durability"), PersistentDataType.INTEGER, 12);
        item.setItemMeta(meta);

        String result = ItemLogFormatter.format(item, 1, LogDetail.BASIC, List.of("lumaitems:*", "lumaitems:itemid"));

        assertEquals(1, countOccurrences(result, "pdc={"), "all PDC entries belong in one pdc={...} group");
        assertEquals(1, countOccurrences(result, "lumaitems:itemid="), "a key matched twice must not be recorded twice");
        assertTrue(result.contains("lumaitems:durability=12"), "should record the integer value");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }

    @Test
    void detailedRecordsPlayerHeadOwner() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "Notch");
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);

        String detailed = ItemLogFormatter.format(head, 1, LogDetail.DETAILED, List.of());
        assertTrue(detailed.contains("head_owner=\"Notch\""), "DETAILED should record the head owner");

        String basic = ItemLogFormatter.format(head, 1, LogDetail.BASIC, List.of());
        assertFalse(basic.contains("head_owner"), "BASIC must not record the head owner");
    }

    @Test
    void detailedRecordsDyedLeatherColor() {
        ItemStack armor = new ItemStack(Material.LEATHER_CHESTPLATE, 1);
        LeatherArmorMeta meta = (LeatherArmorMeta) armor.getItemMeta();
        meta.setColor(Color.RED);
        armor.setItemMeta(meta);

        String result = ItemLogFormatter.format(armor, 1, LogDetail.DETAILED, List.of());
        assertTrue(result.contains("leather_color=#FF0000"), "DETAILED should record the dyed leather color");
    }

    @Test
    void detailedRecordsDurabilityAsRemainingOverMax() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD, 1); // max durability 1561
        ItemMeta meta = sword.getItemMeta();
        ((Damageable) meta).setDamage(61);
        sword.setItemMeta(meta);

        String result = ItemLogFormatter.format(sword, 1, LogDetail.DETAILED, List.of());
        assertTrue(result.contains("durability=1500/1561"), "should record remaining/max durability");
        assertFalse(result.contains("damage="), "should no longer record raw damage");
    }

    @Test
    void detailedRecordsFireworkPowerAndEffects() {
        ItemStack fw = new ItemStack(Material.FIREWORK_ROCKET, 1);
        FireworkMeta meta = (FireworkMeta) fw.getItemMeta();
        meta.setPower(2);
        meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).build());
        fw.setItemMeta(meta);

        String result = ItemLogFormatter.format(fw, 1, LogDetail.DETAILED, List.of());
        assertTrue(result.contains("firework_power=2"), "should record firework flight power");
        assertTrue(result.contains("ball_large"), "should record firework effect shape");
    }

    @Test
    void detailedRecordsBannerPatterns() {
        ItemStack banner = new ItemStack(Material.WHITE_BANNER, 1);
        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        meta.addPattern(new Pattern(DyeColor.RED, PatternType.CREEPER));
        banner.setItemMeta(meta);

        String result = ItemLogFormatter.format(banner, 1, LogDetail.DETAILED, List.of());
        assertTrue(result.contains("banner_patterns=["), "should record banner patterns");
        assertTrue(result.contains("RED"), "should record the pattern color");
    }

    @Test
    void undyedLeatherArmorRecordsNoColor() {
        ItemStack armor = new ItemStack(Material.LEATHER_BOOTS, 1);
        String result = ItemLogFormatter.format(armor, 1, LogDetail.DETAILED, List.of());
        assertFalse(result.contains("leather_color"), "undyed leather must not record a color");
    }

    @Test
    void logsBundleContentsWithTheirOwnDataWhenRecursionEnabled() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD, 1);
        ItemMeta swordMeta = sword.getItemMeta();
        swordMeta.displayName(Component.text("Doom"));
        sword.setItemMeta(swordMeta);

        ItemStack bundle = new ItemStack(Material.BUNDLE, 1);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        meta.addItem(new ItemStack(Material.DIAMOND, 16));
        meta.addItem(sword);
        bundle.setItemMeta(meta);

        String result = ItemLogFormatter.format(bundle, 1, LogDetail.BASIC, List.of(), 1);
        assertTrue(result.contains("contents=["), "should record container contents");
        assertTrue(result.contains("16x Diamond"), "should record a contained item's amount and material");
        assertTrue(result.contains("name=\"Doom\""), "should record custom data of contained items");
    }

    @Test
    void containerContentsOmittedWhenRecursionDisabled() {
        ItemStack bundle = new ItemStack(Material.BUNDLE, 1);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        meta.addItem(new ItemStack(Material.DIAMOND, 16));
        bundle.setItemMeta(meta);

        assertFalse(ItemLogFormatter.format(bundle, 1, LogDetail.BASIC, List.of(), 0).contains("contents"),
                "depth 0 must not expand container contents");
        assertFalse(ItemLogFormatter.format(bundle, 1, LogDetail.BASIC, List.of()).contains("contents"),
                "the 4-arg overload defaults to no container recursion");
    }

    @Test
    void nestedContainersExpandOnlyUpToConfiguredDepth() {
        ItemStack inner = new ItemStack(Material.BUNDLE, 1);
        BundleMeta innerMeta = (BundleMeta) inner.getItemMeta();
        innerMeta.addItem(new ItemStack(Material.EMERALD, 5));
        inner.setItemMeta(innerMeta);

        ItemStack outer = new ItemStack(Material.BUNDLE, 1);
        BundleMeta outerMeta = (BundleMeta) outer.getItemMeta();
        outerMeta.addItem(inner);
        outer.setItemMeta(outerMeta);

        String depth1 = ItemLogFormatter.format(outer, 1, LogDetail.BASIC, List.of(), 1);
        assertTrue(depth1.contains("Bundle"), "depth 1 should list the nested bundle");
        assertFalse(depth1.contains("Emerald"), "depth 1 must not expand the nested bundle's contents");

        String depth2 = ItemLogFormatter.format(outer, 1, LogDetail.BASIC, List.of(), 2);
        assertTrue(depth2.contains("5x Emerald"), "depth 2 should expand nested container contents");
    }

    @Test
    void expandsAFilledBundleNestedInsideAShulkerBox() {
        ItemStack bundle = new ItemStack(Material.BUNDLE, 1);
        BundleMeta bundleMeta = (BundleMeta) bundle.getItemMeta();
        bundleMeta.addItem(new ItemStack(Material.GOLD_INGOT, 7));
        bundle.setItemMeta(bundleMeta);

        ItemStack shulker = new ItemStack(Material.SHULKER_BOX, 1);
        BlockStateMeta shulkerMeta = (BlockStateMeta) shulker.getItemMeta();
        ShulkerBox state = (ShulkerBox) shulkerMeta.getBlockState();
        state.getInventory().addItem(bundle);
        shulkerMeta.setBlockState(state);
        shulker.setItemMeta(shulkerMeta);

        String depth1 = ItemLogFormatter.format(shulker, 1, LogDetail.BASIC, List.of(), 1);
        assertTrue(depth1.contains("Bundle"), "depth 1 should list the bundle inside the shulker");
        assertFalse(depth1.contains("Gold Ingot"), "depth 1 must not expand the bundle's contents");

        String depth2 = ItemLogFormatter.format(shulker, 1, LogDetail.BASIC, List.of(), 2);
        assertTrue(depth2.contains("7x Gold Ingot"), "depth 2 should expand a bundle nested in a shulker box");
    }

    @Test
    void ignoresPersistentDataKeysThatArePresentButNotConfigured() {
        ItemStack item = new ItemStack(Material.DIAMOND, 1);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(
                new NamespacedKey("lumaitems", "itemid"), PersistentDataType.STRING, "magic_sword");
        item.setItemMeta(meta);

        String result = ItemLogFormatter.format(item, 1, LogDetail.BASIC, List.of());
        assertFalse(result.contains("magic_sword"), "unconfigured PDC keys must not be recorded");
    }
}
