package dev.jsinco.malts.logging;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.jsinco.malts.utility.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.block.banner.Pattern;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

public final class ItemLogFormatter {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ItemLogFormatter() {
    }

    private static final PersistentDataType<?, ?>[] SIMPLE_PDC_TYPES = {
            PersistentDataType.STRING,
            PersistentDataType.BOOLEAN,
            PersistentDataType.BYTE,
            PersistentDataType.SHORT,
            PersistentDataType.INTEGER,
            PersistentDataType.LONG,
            PersistentDataType.FLOAT,
            PersistentDataType.DOUBLE
    };

    public static String format(ItemStack item, int amount, LogDetail detail, List<String> persistentDataKeys) {
        return format(item, amount, detail, persistentDataKeys, 0);
    }

    public static String format(ItemStack item, int amount, LogDetail detail, List<String> persistentDataKeys, int containerDepth) {
        StringBuilder sb = new StringBuilder();
        sb.append(amount).append("x ").append(Util.formatEnumerator(item.getType().toString()));

        if (!item.hasItemMeta()) {
            return sb.toString();
        }

        ItemMeta meta = item.getItemMeta();
        List<String> custom = new ArrayList<>();

        if (meta.hasCustomModelData()) {
            custom.add("cmd=" + meta.getCustomModelData());
        }
        if (meta.hasDisplayName()) {
            custom.add("name=\"" + plain(meta.displayName()) + "\"");
        }

        if (detail == LogDetail.DETAILED) {
            appendDetailed(item, meta, custom);
        }

        appendPersistentData(meta, custom, persistentDataKeys);

        if (containerDepth > 0) {
            appendContainerContents(meta, custom, detail, persistentDataKeys, containerDepth);
        }

        if (!custom.isEmpty()) {
            sb.append(" {").append(String.join(", ", custom)).append('}');
        }
        return sb.toString();
    }

    private static void appendContainerContents(ItemMeta meta, List<String> custom, LogDetail detail,
                                                List<String> persistentDataKeys, int containerDepth) {
        List<ItemStack> contents = containerContents(meta);
        if (contents == null) {
            return;
        }
        StringJoiner joiner = new StringJoiner(" | ", "contents=[", "]");
        boolean any = false;
        for (ItemStack content : contents) {
            if (content == null || content.getType().isAir()) {
                continue;
            }
            joiner.add(format(content, content.getAmount(), detail, persistentDataKeys, containerDepth - 1));
            any = true;
        }
        if (any) {
            custom.add(joiner.toString());
        }
    }

    private static List<ItemStack> containerContents(ItemMeta meta) {
        if (meta instanceof BundleMeta bundle) {
            return bundle.hasItems() ? bundle.getItems() : null;
        }
        if (meta instanceof BlockStateMeta state && state.hasBlockState() && state.getBlockState() instanceof Container container) {
            return Arrays.asList(container.getInventory().getContents());
        }
        return null;
    }

    private static void appendDetailed(ItemStack item, ItemMeta meta, List<String> custom) {
        if (meta.hasLore()) {
            StringJoiner lore = new StringJoiner(" | ", "lore=[", "]");
            for (Component line : meta.lore()) {
                lore.add(plain(line));
            }
            custom.add(lore.toString());
        }

        Map<Enchantment, Integer> enchants = item.getEnchantments();
        if (meta instanceof EnchantmentStorageMeta stored && stored.hasStoredEnchants()) {
            enchants = stored.getStoredEnchants();
        }
        if (!enchants.isEmpty()) {
            StringJoiner ench = new StringJoiner(", ", "enchants=[", "]");
            enchants.forEach((e, lvl) -> ench.add(e.getKey().getKey() + " " + lvl));
            custom.add(ench.toString());
        }

        if (meta.isUnbreakable()) {
            custom.add("unbreakable");
        }
        if (meta instanceof Damageable damageable && damageable.hasDamage()) {
            int max = damageable.hasMaxDamage() ? damageable.getMaxDamage() : item.getType().getMaxDurability();
            custom.add("durability=" + (max - damageable.getDamage()) + "/" + max);
        }

        if (meta instanceof SkullMeta skull) {
            appendSkull(skull, custom);
        }
        if (meta instanceof BookMeta book) {
            appendBook(book, custom);
        }
        if (meta instanceof PotionMeta potion) {
            appendPotion(potion, custom);
        }
        if (meta instanceof LeatherArmorMeta leather) {
            appendLeatherColor(leather, custom);
        }
        if (meta instanceof ArmorMeta armor && armor.hasTrim()) {
            ArmorTrim trim = armor.getTrim();
            custom.add("trim=" + key(trim.getPattern()) + "/" + key(trim.getMaterial()));
        }
        if (meta instanceof BannerMeta banner) {
            appendBanner(banner, custom);
        }
        if (meta instanceof FireworkMeta firework) {
            appendFirework(firework, custom);
        }
    }

    private static void appendSkull(SkullMeta skull, List<String> custom) {
        PlayerProfile profile = skull.getPlayerProfile();
        if (profile == null) {
            return;
        }
        String name = profile.getName();
        UUID id = profile.getId();
        if (name != null && !name.isEmpty()) {
            custom.add("head_owner=\"" + name + "\"" + (id != null ? " (" + id + ")" : ""));
        } else if (id != null) {
            custom.add("head_owner=" + id);
        }
        String texture = textureUrl(profile);
        if (texture != null) {
            custom.add("head_texture=" + texture);
        }
    }

    private static String textureUrl(PlayerProfile profile) {
        for (ProfileProperty property : profile.getProperties()) {
            if (!"textures".equals(property.getName())) {
                continue;
            }
            try {
                String json = new String(Base64.getDecoder().decode(property.getValue()), StandardCharsets.UTF_8);
                JsonObject textures = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");
                JsonObject skin = textures == null ? null : textures.getAsJsonObject("SKIN");
                JsonElement url = skin == null ? null : skin.get("url");
                return url == null ? null : url.getAsString();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static void appendBook(BookMeta book, List<String> custom) {
        if (book.hasTitle()) {
            custom.add("book_title=\"" + plain(book.title()) + "\"");
        }
        if (book.hasAuthor()) {
            custom.add("book_author=\"" + plain(book.author()) + "\"");
        }
        if (book.hasGeneration() && book.getGeneration() != null) {
            custom.add("book_generation=" + book.getGeneration());
        }
    }

    private static void appendPotion(PotionMeta potion, List<String> custom) {
        PotionType base = potion.getBasePotionType();
        if (base != null) {
            custom.add("potion=" + base.getKey().getKey());
        }
        if (potion.hasCustomEffects()) {
            StringJoiner effects = new StringJoiner(", ", "potion_effects=[", "]");
            for (PotionEffect effect : potion.getCustomEffects()) {
                effects.add(effect.getType().getKey().getKey() + " " + (effect.getAmplifier() + 1));
            }
            custom.add(effects.toString());
        }
        if (potion.hasColor()) {
            custom.add("potion_color=" + hex(potion.getColor()));
        }
    }

    private static void appendLeatherColor(LeatherArmorMeta leather, List<String> custom) {
        Color color = leather.getColor();
        if (!color.equals(Bukkit.getItemFactory().getDefaultLeatherColor())) {
            custom.add("leather_color=" + hex(color));
        }
    }

    private static void appendBanner(BannerMeta banner, List<String> custom) {
        List<Pattern> patterns = banner.getPatterns();
        if (patterns.isEmpty()) {
            return;
        }
        StringJoiner joiner = new StringJoiner(", ", "banner_patterns=[", "]");
        for (Pattern pattern : patterns) {
            joiner.add(pattern.getColor() + " " + key(pattern.getPattern()));
        }
        custom.add(joiner.toString());
    }

    private static void appendFirework(FireworkMeta firework, List<String> custom) {
        custom.add("firework_power=" + firework.getPower());
        if (!firework.hasEffects()) {
            return;
        }
        StringJoiner joiner = new StringJoiner(", ", "firework_effects=[", "]");
        for (FireworkEffect effect : firework.getEffects()) {
            joiner.add(effect.getType().name().toLowerCase());
        }
        custom.add(joiner.toString());
    }

    private static String hex(Color color) {
        return String.format("#%06X", color.asRGB() & 0xFFFFFF);
    }

    private static String key(Keyed keyed) {
        return keyed.getKey().getKey();
    }

    private static void appendPersistentData(ItemMeta meta, List<String> custom, List<String> persistentDataKeys) {
        if (persistentDataKeys.isEmpty()) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (String raw : persistentDataKeys) {
            if (raw.endsWith(":*") || !raw.contains(":")) {
                appendNamespace(pdc, custom, raw.endsWith(":*") ? raw.substring(0, raw.length() - 2) : raw);
            } else {
                appendExactKey(pdc, custom, raw);
            }
        }
    }

    private static void appendNamespace(PersistentDataContainer pdc, List<String> custom, String namespace) {
        for (NamespacedKey present : pdc.getKeys()) {
            if (present.getNamespace().equals(namespace)) {
                custom.add(present.toString());
            }
        }
    }

    private static void appendExactKey(PersistentDataContainer pdc, List<String> custom, String raw) {
        NamespacedKey key = NamespacedKey.fromString(raw);
        if (key == null) {
            return;
        }
        String value = readSimple(pdc, key);
        if (value != null) {
            custom.add(raw + "=" + value);
        }
    }

    private static String readSimple(PersistentDataContainer pdc, NamespacedKey key) {
        for (PersistentDataType<?, ?> type : SIMPLE_PDC_TYPES) {
            if (pdc.has(key, type)) {
                return String.valueOf(pdc.get(key, type));
            }
        }
        return null;
    }

    private static String plain(Component component) {
        return PLAIN.serialize(component).replace('\n', ' ');
    }
}
