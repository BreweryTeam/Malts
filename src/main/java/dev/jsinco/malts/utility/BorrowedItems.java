package dev.jsinco.malts.utility;

import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.files.Lang;
import dev.jsinco.malts.integration.external.NexoIntegration;
import dev.jsinco.malts.registry.Registry;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
        if (candidate.getAmount() < placeholder.getAmount()) {
            return false;
        }
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

        // Built from scratch so no enchantments, contents or foreign PDC keys carry over
        ItemStack placeholder = ItemStack.of(real.getType(), real.getAmount());
        placeholder.editMeta(meta -> {
            if (source.hasCustomName()) meta.customName(source.customName());
            if (source.hasItemName()) meta.itemName(source.itemName());
            if (source.hasCustomModelDataComponent()) meta.setCustomModelDataComponent(source.getCustomModelDataComponent());

            NexoIntegration nexo = Registry.INTEGRATIONS.get(NexoIntegration.class);
            NamespacedKey fadedModel = nexo != null && !source.hasItemModel() ? nexo.fadedModel(real.getType()) : null;
            if (fadedModel != null) {
                meta.setItemModel(fadedModel);
            } else if (source.hasItemModel()) {
                meta.setItemModel(source.getItemModel());
            }

            List<Component> lore = source.hasLore() ? source.lore() : List.of();
            meta.lore(lore);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(PLACEHOLDER, PersistentDataType.STRING, id);
            pdc.set(BORROWER, PersistentDataType.STRING, borrower.getUniqueId().toString());
            pdc.set(BORROWER_NAME, PersistentDataType.STRING, borrower.getName());
            pdc.set(BORROWED_AT, PersistentDataType.LONG, System.currentTimeMillis());
            pdc.set(ORIGINAL_LORE_SIZE, PersistentDataType.INTEGER, lore != null ? lore.size() : 0);
            pdc.set(ORIGINAL, PersistentDataType.BYTE_ARRAY, normalize(real).serializeAsBytes());
        });
        refreshLore(placeholder);
        return placeholder;
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
