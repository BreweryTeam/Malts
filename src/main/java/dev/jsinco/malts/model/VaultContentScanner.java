package dev.jsinco.malts.model;

import com.google.common.base.Preconditions;
import dev.jsinco.malts.commands.subcommands.SearchCommand;
import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.IntPair;
import dev.jsinco.malts.configuration.files.GuiConfig;
import dev.jsinco.malts.configuration.files.Lang;
import dev.jsinco.malts.utility.Couple;
import dev.jsinco.malts.utility.Text;
import dev.jsinco.malts.utility.Util;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Scans a collection of vaults for a given item(s) based
 * on certain criteria.
 *
 * @see SearchCommand
 * @see Vault
 */
public record VaultContentScanner(Collection<Vault> vaults, @Nullable IntPair range, @Nullable String who) {

    private static final IntPair RANGE_PER_PAGE = IntPair.of(1, 6);
    private static final int MAX_CONTAINER_DEPTH = 8; // Bundles can be nested inside each other
    private static final int MAX_CONTAINER_SLOTS = 256; // Vanilla limit of the container data component
    private static final GuiConfig GUI_CONFIG = ConfigManager.get(GuiConfig.class);

    public VaultContentScanner(Collection<Vault> vaults, int page, @Nullable String who) {
        this(vaults, rangeForPage(page), who);
    }


    public ResultCollection matchingVaults(String plainText) {
        String searchFor = plainText.toLowerCase().strip();
        List<Result> results = vaults.stream()
                .map(vault -> {
                    List<Match> matches = new ArrayList<>();
                    matches(Arrays.asList(vault.getInventory().getContents()), searchFor, List.of(), matches);

                    if (matches.isEmpty()) return null;

                    return new Result(vault, matches, who);
                })
                .filter(Objects::nonNull)
                .toList();
        return new ResultCollection(results, range, plainText, who);
    }


    private void matches(List<ItemStack> items, String searchFor, List<ItemStack> containers, List<Match> matches) {
        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) continue;

            if (matchesSearch(item, searchFor)) {
                matches.add(new Match(item, containers));
            }
            if (containers.size() < MAX_CONTAINER_DEPTH) {
                List<ItemStack> contents = contents(item);
                if (contents.isEmpty()) continue;

                List<ItemStack> nestedContainers = new ArrayList<>(containers);
                nestedContainers.add(item);
                matches(contents, searchFor, List.copyOf(nestedContainers), matches);
            }
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private List<ItemStack> contents(ItemStack itemStack) {
        ItemContainerContents container = itemStack.getData(DataComponentTypes.CONTAINER);
        if (container != null) {
            return container.contents();
        }
        BundleContents bundle = itemStack.getData(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null) {
            return bundle.contents();
        }
        return List.of();
    }

    private boolean matchesSearch(ItemStack itemStack, String searchFor) {
        return hasMatchingName(itemStack, searchFor)
                || hasMatchingMaterialName(itemStack, searchFor)
                || hasMatchingLore(itemStack, searchFor);
    }


    private boolean hasMatchingName(ItemStack itemStack, String plainText) {
        final String searchFor = plainText.toLowerCase().strip();
        String plainItemName = PlainTextComponentSerializer.plainText()
                .serialize(itemStack.effectiveName())
                .toLowerCase()
                .strip();
        return plainItemName.contains(searchFor);
    }

    private boolean hasMatchingMaterialName(ItemStack itemStack, String plainText) {
        final String searchFor = plainText.toLowerCase().strip().replace(" ", "_");
        String materialName = itemStack.getType().name().toLowerCase();
        return materialName.contains(searchFor);
    }

    private boolean hasMatchingLore(ItemStack itemStack, String plainText) {
        final String searchFor = plainText.toLowerCase().strip();
        if (!itemStack.hasItemMeta() || !itemStack.getItemMeta().hasLore()) {
            return false;
        }
        List<Component> lore = Preconditions.checkNotNull(itemStack.getItemMeta().lore());

        for (Component loreLine : lore) {
            String plainLoreLine = PlainTextComponentSerializer.plainText()
                    .serialize(loreLine)
                    .toLowerCase()
                    .strip();
            if (plainLoreLine.contains(searchFor)) {
                return true;
            }
        }
        return false;
    }

    public static IntPair rangeForPage(int page) {
        int perPage = RANGE_PER_PAGE.b();
        int start = (page - 1) * perPage + 1;
        int end = page * perPage;
        return IntPair.of(start, end);
    }

    public static int pageForRange(@Nullable IntPair range) {
        if (range == null) return 1;
        int perPage = RANGE_PER_PAGE.b();
        return (int) Math.ceil((double) range.a() / perPage);
    }


    @RequiredArgsConstructor
    public static class ResultCollection {
        private static final Lang lang = ConfigManager.get(Lang.class);

        @Getter
        private final List<Result> results;
        @Getter
        private final IntPair range;
        @Getter
        private final String query;
        private final @Nullable String who;

        private final Component previousPageComponent = lang.entry(l -> l.command().search().previousPage(), "»");
        private final Component nextPageComponent = lang.entry(l -> l.command().search().nextPage(), "»");


        public Component queryResultSummary() {
            if (query == null || query.isEmpty() || results.isEmpty()) {
                return Preconditions.checkNotNull(lang.entry(l -> l.command().search().noResults(), true, Couple.of("{query}", query)));
            }

            int maxPages = (int) Math.ceil((double) totalItemsFound() / RANGE_PER_PAGE.b());
            int page = Math.min(pageForRange(range), maxPages == 0 ? 1 : maxPages);

            List<Component> resultsFormatted = resultsFormatted();


            Component previousPage = previousPageComponent
                    .clickEvent(ClickEvent.runCommand(this.searchCommand(page - 1)))
                    .hoverEvent(HoverEvent.showText(previousPageComponent));
            Component nextPage = nextPageComponent
                    .clickEvent(ClickEvent.runCommand(this.searchCommand(page + 1)))
                    .hoverEvent(HoverEvent.showText(nextPageComponent));
            Component base = lang.entry(l -> lang.command().search().results(), true,
                    Couple.of("{amount}", this.totalItemsFound()),
                    Couple.of("{query}", query),
                    Couple.of("{page}", page),
                    Couple.of("{maxPages}", maxPages)
            );

            return Util.replaceComponents(base,
                    Couple.of("{results}", resultsFormatted),
                    Couple.of("{previousPage}", previousPage),
                    Couple.of("{nextPage}", nextPage)
            );
        }

        public List<Component> resultsFormatted() {
            String format = lang.command().search().resultFormat();
            String nestedFormat = lang.command().search().nestedResultFormat();
            Component containerSeparator = Text.mm(lang.command().search().containerSeparator());
            List<Component> resultsFormatted = new ArrayList<>();
            for (Result result : this.results) {
                List<Component> formattedItems = result.formatMatchingItems(format, nestedFormat, containerSeparator, result.getVault());
                resultsFormatted.addAll(formattedItems);
            }

            // limit output based on range
            if (range != null) {
                int total = resultsFormatted.size();
                if (total == 0) return resultsFormatted;

                int startIndex = Math.max(range.a() - 1, 0);
                int endIndex = Math.min(range.b(), total);
                if (startIndex >= total) {
                    int perPage = RANGE_PER_PAGE.b();
                    startIndex = Math.max(total - perPage, 0);
                    endIndex = total;
                }
                endIndex = Math.max(endIndex, startIndex);
                return resultsFormatted.subList(startIndex, endIndex);
            }
            return resultsFormatted;
        }

        public int totalItemsFound() {
            return results.stream()
                    .mapToInt(result -> result.getMatchingItems().size())
                    .sum();
        }

        private String searchCommand(int page) {
            return "malts search " + query + " -page " + page + (who != null && !who.isEmpty() ? " -player " + who : "");
        }
    }

    // Outermost first, empty if the item is directly in the vault
    public record Match(ItemStack item, List<ItemStack> containers) {
    }

    @AllArgsConstructor
    public static class Result {

        @Getter
        private final Vault vault;
        @Getter
        private final List<Match> matchingItems;
        private final @Nullable String otherPlayer;

        public List<Component> formatMatchingItems(String format, String nestedFormat, Component containerSeparator, Vault vault) {
            Component vaultName = Text.mm(vault.getCustomName()).hoverEvent(vaultPreview(vault).asHoverEvent());
            return matchingItems.stream()
                    .map(match -> {
                        ItemStack itemStack = match.item();
                        Component containers = Component.join(
                                JoinConfiguration.separator(containerSeparator),
                                match.containers().stream()
                                        .map(container -> container.effectiveName().hoverEvent(container.asHoverEvent()))
                                        .toList()
                        );
                        return Util.replaceComponents(
                                        Text.mm(match.containers().isEmpty() ? format : nestedFormat),
                                        Couple.of("{itemName}", itemStack.effectiveName().hoverEvent(itemStack.asHoverEvent())),
                                        Couple.of("{amount}", String.valueOf(itemStack.getAmount())),
                                        Couple.of("{vaultName}", vaultName),
                                        Couple.of("{containers}", containers)
                                )
                                .clickEvent(ClickEvent.runCommand(this.vaultCommand()));
                    })
                    .toList();
        }

        // A chest item named after the vault holding its contents (for container preview mods)
        @SuppressWarnings("UnstableApiUsage")
        private static ItemStack vaultPreview(Vault vault) {
            List<ItemStack> contents = Arrays.stream(vault.getInventory().getContents())
                    .limit(MAX_CONTAINER_SLOTS)
                    .map(item -> item == null ? ItemStack.empty() : item)
                    .toList();
            ItemStack preview = ItemStack.of(Material.CHEST);
            preview.setData(DataComponentTypes.CUSTOM_NAME, Text.mm("<!i>" + GUI_CONFIG
                    .yourVaultsGui().vaultItem().name().replace("{vaultName}", vault.getCustomName())));
            preview.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(contents));
            return preview;
        }

        private String vaultCommand() {
            // TODO: Make configurable
            if (otherPlayer != null && !otherPlayer.isEmpty()) {
                return "malts vaultother " + otherPlayer + " " + vault.getId();
            } else {
                return "malts vaults " + vault.getId();
            }
        }
    }
}
