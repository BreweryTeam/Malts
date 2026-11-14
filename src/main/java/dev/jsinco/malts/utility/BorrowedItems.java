package dev.jsinco.malts.utility;

import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.files.Lang;
import dev.jsinco.malts.integration.external.NexoIntegration;
import dev.jsinco.malts.registry.Registry;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.tags.EnchantmentTagKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public final class BorrowedItems {

    private static final Lang LANG = ConfigManager.get(Lang.class);
    private static final List<String> DEFAULT_LORE = List.of("", "<gray>Borrowed by <aqua>{name}", "<gray>{date} <dark_gray>({timeAgo} ago)");
    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm";

    // On placeholders
    public static final NamespacedKey PLACEHOLDER = Util.namespacedKey("borrowed_placeholder");
    private static final NamespacedKey BORROWER = Util.namespacedKey("borrower");
    private static final NamespacedKey BORROWER_NAME = Util.namespacedKey("borrower_name");
    private static final NamespacedKey BORROWED_AT = Util.namespacedKey("borrowed_at");
    private static final NamespacedKey ORIGINAL_LORE_SIZE = Util.namespacedKey("original_lore_size");
    private static final NamespacedKey ORIGINAL = Util.namespacedKey("original");

    private BorrowedItems() {
    }

    public static boolean isPlaceholder(@Nullable ItemStack item) {
        return item != null && !item.isEmpty() && Util.hasPersistentKey(item, PLACEHOLDER);
    }

    public static boolean canReturn(@Nullable ItemStack candidate, @Nullable ItemStack placeholder) {
        if (candidate == null || candidate.isEmpty() || !isPlaceholder(placeholder)) {
            return false;
        }
        return candidate.getAmount() >= placeholder.getAmount() && matches(candidate, placeholder);
    }

    // Players can only return to their own placeholders, or to any if they have none for this item
    public static boolean canReturn(@Nullable ItemStack candidate, @Nullable ItemStack placeholder, Player player, Inventory vault) {
        return canReturn(candidate, placeholder) && (borrowedBy(placeholder, player) || !hasBorrowed(candidate, player, vault));
    }

    public static boolean hasBorrowed(ItemStack candidate, Player player, Inventory vault) {
        for (ItemStack item : vault.getContents()) {
            if (isPlaceholder(item) && borrowedBy(item, player) && matches(candidate, item)) {
                return true;
            }
        }
        return false;
    }

    public static boolean borrowedBy(ItemStack placeholder, Player player) {
        return player.getUniqueId().toString().equals(Util.getPersistentKey(placeholder, BORROWER, PersistentDataType.STRING));
    }

    private static boolean matches(ItemStack candidate, ItemStack placeholder) {
        byte[] original = Util.getPersistentKey(placeholder, ORIGINAL, PersistentDataType.BYTE_ARRAY);
        if (original == null) {
            return false;
        }
        return canonicalize(normalize(candidate)).isSimilar(canonicalize(normalize(ItemStack.deserializeBytes(original))));
    }

    // Takes the placeholder's amount from the candidate and returns the item to put back into the vault
    public static ItemStack takeReturn(ItemStack candidate, ItemStack placeholder) {
        ItemStack returned = candidate.clone();
        returned.setAmount(placeholder.getAmount());
        candidate.setAmount(candidate.getAmount() - placeholder.getAmount());
        return returned;
    }

    // Strips what may differ between effectively identical items
    private static ItemStack normalize(ItemStack item) {
        ItemStack copy = item.asOne();
        copy.resetData(DataComponentTypes.DAMAGE);
        copy.resetData(DataComponentTypes.REPAIR_COST);
        copy.editMeta(meta -> {
            if (meta.hasCustomName()) meta.customName(canonicalize(meta.customName()));
            if (meta.hasItemName()) meta.itemName(canonicalize(meta.itemName()));
            if (meta.hasLore()) meta.lore(meta.lore().stream().map(BorrowedItems::canonicalize).toList());
        });
        return copy;
    }

    // Necessary for robust comparison - do not remove!
    private static ItemStack canonicalize(ItemStack item) {
        return ItemStack.deserializeBytes(item.serializeAsBytes());
    }

    // Necessary for comparing effective formatting
    private static Component canonicalize(Component component) {
        List<Component> segments = new ArrayList<>();
        flatten(component, Style.empty(), segments);
        return Component.text().append(segments).build();
    }

    private static void flatten(Component component, Style parentStyle, List<Component> segments) {
        Style style = parentStyle.merge(component.style());
        if (component instanceof TextComponent text) {
            if (!text.content().isEmpty()) {
                int last = segments.size() - 1;
                if (last >= 0 && segments.get(last) instanceof TextComponent previous && previous.style().equals(style)) {
                    segments.set(last, previous.content(previous.content() + text.content()));
                } else {
                    segments.add(Component.text(text.content(), style));
                }
            }
        } else {
            segments.add(component.children(List.of()).style(style));
        }
        for (Component child : component.children()) {
            flatten(child, style, segments);
        }
    }

    // Creates the placeholder that stays in the vault while the item is borrowed
    public static ItemStack borrow(ItemStack real, Player borrower) {
        String id = UUID.randomUUID().toString();
        ItemMeta source = real.getItemMeta();

        // An item model pointing to a vanilla item is shown as that item
        Material vanillaModel = source.hasItemModel() ? vanillaItem(source.getItemModel()) : null;
        Material type = vanillaModel != null ? vanillaModel : real.getType();

        // Built from scratch so no enchantments, contents or foreign PDC keys carry over
        ItemStack placeholder = ItemStack.of(type);
        if (type != real.getType()) {
            placeholder.setData(DataComponentTypes.MAX_STACK_SIZE, real.getMaxStackSize());
        }
        placeholder.setAmount(real.getAmount());
        placeholder.editMeta(meta -> {
            if (source.hasCustomName()) meta.customName(source.customName());
            if (source.hasItemName()) {
                meta.itemName(source.itemName());
            } else if (type != real.getType()) {
                meta.itemName(Component.translatable(real.translationKey()));
            }
            if (source.hasCustomModelDataComponent()) meta.setCustomModelDataComponent(source.getCustomModelDataComponent());

            // Custom model data needs the resource pack's own definition, which the faded model would replace
            NexoIntegration nexo = Registry.INTEGRATIONS.get(NexoIntegration.class);
            boolean customModel = (source.hasItemModel() && vanillaModel == null) || source.hasCustomModelDataComponent();
            NamespacedKey fadedModel = nexo != null && !customModel ? nexo.fadedModel(type) : null;
            if (fadedModel != null) {
                meta.setItemModel(fadedModel);
            } else if (source.hasItemModel() && vanillaModel == null) {
                meta.setItemModel(source.getItemModel());
            }

            List<Component> lore = new ArrayList<>(enchantmentLines(real, source));
            if (source.hasLore()) lore.addAll(source.lore());
            meta.lore(lore);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(PLACEHOLDER, PersistentDataType.STRING, id);
            pdc.set(BORROWER, PersistentDataType.STRING, borrower.getUniqueId().toString());
            pdc.set(BORROWER_NAME, PersistentDataType.STRING, borrower.getName());
            pdc.set(BORROWED_AT, PersistentDataType.LONG, System.currentTimeMillis());
            pdc.set(ORIGINAL_LORE_SIZE, PersistentDataType.INTEGER, lore.size());
            pdc.set(ORIGINAL, PersistentDataType.BYTE_ARRAY, normalize(real).serializeAsBytes());
        });
        TooltipDisplay tooltip = real.getData(DataComponentTypes.TOOLTIP_DISPLAY);
        if (tooltip != null) {
            placeholder.setData(DataComponentTypes.TOOLTIP_DISPLAY, tooltip);
        }
        refreshLore(placeholder);
        return placeholder;
    }

    private static List<Component> enchantmentLines(ItemStack real, ItemMeta source) {
        TooltipDisplay tooltip = real.getData(DataComponentTypes.TOOLTIP_DISPLAY);
        if (tooltip != null && tooltip.hideTooltip()) return List.of();
        Set<DataComponentType> hidden = tooltip != null ? tooltip.hiddenComponents() : Set.of();

        Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
        if (source instanceof EnchantmentStorageMeta storage && !hidden.contains(DataComponentTypes.STORED_ENCHANTMENTS)) {
            enchants.putAll(storage.getStoredEnchants());
        }
        if (!hidden.contains(DataComponentTypes.ENCHANTMENTS)) {
            source.getEnchants().forEach((enchantment, level) -> enchants.merge(enchantment, level, Math::max));
        }
        if (enchants.isEmpty()) return List.of();

        // Vanilla lists enchantments in the tooltip_order tag first
        List<Enchantment> ordered = new ArrayList<>();
        try {
            for (Enchantment enchantment : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).getTagValues(EnchantmentTagKeys.TOOLTIP_ORDER)) {
                if (enchants.containsKey(enchantment)) ordered.add(enchantment);
            }
        } catch (RuntimeException ignored) {
        }
        for (Enchantment enchantment : enchants.keySet()) {
            if (!ordered.contains(enchantment)) ordered.add(enchantment);
        }

        List<Component> lines = new ArrayList<>(ordered.size());
        for (Enchantment enchantment : ordered) {
            lines.add(enchantment.displayName(enchants.get(enchantment)).decoration(TextDecoration.ITALIC, false));
        }
        return lines;
    }

    @Nullable
    private static Material vanillaItem(NamespacedKey model) {
        if (model == null) return null;
        if (!model.getNamespace().equals(NamespacedKey.MINECRAFT)) return null;
        Material material = Material.matchMaterial(model.getKey());
        return material != null && material.isItem() && !material.isAir() ? material : null;
    }

    public static void refreshLore(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (isPlaceholder(item)) {
                refreshLore(item);
            }
        }
    }

    public static void refreshLore(ItemStack placeholder) {
        placeholder.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            String name = pdc.getOrDefault(BORROWER_NAME, PersistentDataType.STRING, "?");
            long borrowedAt = pdc.getOrDefault(BORROWED_AT, PersistentDataType.LONG, 0L);
            int originalSize = pdc.getOrDefault(ORIGINAL_LORE_SIZE, PersistentDataType.INTEGER, 0);

            List<Component> current = meta.hasLore() ? meta.lore() : List.of();
            List<Component> lore = new ArrayList<>(current.subList(0, Math.min(originalSize, current.size())));

            List<String> template = LANG.vaults().borrowedLore() != null ? LANG.vaults().borrowedLore() : DEFAULT_LORE;
            List<String> lines = Util.replaceAll(template,
                    Couple.of("{name}", name),
                    Couple.of("{date}", formatDate(borrowedAt)),
                    Couple.of("{timeAgo}", timeAgo(borrowedAt))
            );
            lore.addAll(Text.mmlNoItalic(lines));
            meta.lore(lore);
        });
    }

    private static String formatDate(long millis) {
        String pattern = LANG.vaults().borrowedDateFormat();
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(pattern != null ? pattern : DEFAULT_DATE_FORMAT);
        } catch (IllegalArgumentException e) {
            formatter = DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT);
        }
        return formatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    public static String timeAgo(long millis) {
        Duration d = Duration.ofMillis(Math.max(0, System.currentTimeMillis() - millis));
        long days = d.toDays();
        if (days > 0) return days + "d " + d.toHoursPart() + "h";
        if (d.toHours() > 0) return d.toHoursPart() + "h " + d.toMinutesPart() + "m";
        if (d.toMinutes() > 0) return d.toMinutesPart() + "m";
        return d.toSecondsPart() + "s";
    }
}
