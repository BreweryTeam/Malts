package dev.jsinco.malts.model;

import com.google.common.base.Preconditions;
import dev.jsinco.malts.commands.subcommands.SearchCommand;
import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.IntPair;
import dev.jsinco.malts.configuration.files.Lang;
import dev.jsinco.malts.utility.Couple;
import dev.jsinco.malts.utility.Text;
import dev.jsinco.malts.utility.Util;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Scans a collection of vaults for a given item(s) based
 * on certain criteria.
 *
 * @see SearchCommand
 * @see Vault
 */
public record VaultContentScanner(Collection<Vault> vaults, @Nullable IntPair range, @Nullable String who) {

    private static final IntPair RANGE_PER_PAGE = IntPair.of(1, 6);

    public VaultContentScanner(Collection<Vault> vaults, int page, @Nullable String who) {
        this(vaults, rangeForPage(page), who);
    }


    public ResultCollection matchingVaults(String plainText) {
        String searchFor = plainText.toLowerCase().strip();
        List<Result> results = vaults.stream()
                .map(vault -> {
                    List<ItemStack> matches = Arrays.stream(vault.getInventory().getContents())
                            .filter(Objects::nonNull)
                            .filter(item -> matchesSearch(item, searchFor))
                            .collect(Collectors.toList());

                    if (matches.isEmpty()) return null;

                    return new Result(vault, matches, who);
                })
                .filter(Objects::nonNull)
                .toList();
        return new ResultCollection(results, range, plainText, who);
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
            List<Component> resultsFormatted = new ArrayList<>();
            for (Result result : this.results) {
                List<Component> formattedItems = result.formatMatchingItems(format, result.getVault());
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

    @AllArgsConstructor
    public static class Result {

        @Getter
        private final Vault vault;
        @Getter
        private final List<ItemStack> matchingItems;
        private final @Nullable String otherPlayer;

        public List<Component> formatMatchingItems(String format, Vault vault) {
            return matchingItems.stream()
                    .map(itemStack ->
                            Util.replaceComponents(
                                            Text.mm(format),
                                            Couple.of("{itemName}", itemStack.effectiveName()),
                                            Couple.of("{amount}", String.valueOf(itemStack.getAmount())),
                                            Couple.of("{vaultName}", vault.getCustomName())
                                    )
                                    .hoverEvent(itemStack.asHoverEvent())
                                    .clickEvent(ClickEvent.runCommand(this.vaultCommand()))
                    )
                    .toList();
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
