package dev.jsinco.malts.logging.dialog;

import dev.jsinco.malts.logging.LogFilter;
import dev.jsinco.malts.logging.LogQueryResult;
import dev.jsinco.malts.logging.MaltsLogger;
import dev.jsinco.malts.utility.Executors;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO: Potentially make this translatable? Logs don't store any formatting info,
//  so this would require much more intelligent parsing. A translation step could
//  be implemented between interpreting the plain english logs and showing them
//  in game. Alternatively, logs could of course be fully translated.
public final class LogDialog {

    private static final int PAGE_ROWS = 25;
    private static final int BODY_WIDTH = 400;
    private static final int ROW_WIDTH = BODY_WIDTH - 24; // the text box's padding leaves less than BODY_WIDTH for text
    private static final int MAX_RANGE_DAYS = 366;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final NamedTextColor DEFAULT_COLOR = NamedTextColor.WHITE;
    private static final NamedTextColor TIME_COLOR = NamedTextColor.GRAY;
    private static final NamedTextColor WAREHOUSE_COLOR = NamedTextColor.AQUA;
    private static final NamedTextColor VAULT_COLOR = NamedTextColor.GREEN;
    private static final NamedTextColor UUID_COLOR = NamedTextColor.DARK_GRAY;
    private static final NamedTextColor KEYWORD_COLOR = NamedTextColor.RED;
    private static final NamedTextColor PLAYER_COLOR = NamedTextColor.LIGHT_PURPLE;
    private static final NamedTextColor ITEM_COLOR = NamedTextColor.GOLD;
    private static final NamedTextColor QUOTED_COLOR = NamedTextColor.DARK_GREEN;
    private static final NamedTextColor PUNCT_COLOR = NamedTextColor.DARK_GRAY;
    private static final NamedTextColor HIGHLIGHT_COLOR = NamedTextColor.YELLOW;
    private static final NamedTextColor KEY_COLOR = NamedTextColor.DARK_AQUA;
    private static final NamedTextColor PDC_KEY_COLOR = NamedTextColor.BLUE;
    private static final NamedTextColor CONSOLE_COLOR = NamedTextColor.DARK_PURPLE;

    private static final String UUID_REGEX =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private static final String KEYWORD_REGEX =
            "changed warehouse mode from"
                    + "|changed icon of vault \\d+ from"
                    + "|renamed vault \\d+ from"
                    + "|transferred vaults between"
                    + "|(?:added|removed) compartment"
                    + "|(?:into|from|to) (?:vault \\d+|warehouse)"
                    + "|on vault \\d+"
                    + "|deleted vault \\d+"
                    + "|\\b(?:stocked|destocked|deposited|withdrew|untrusted|trusted)\\b"
                    + "|\\b(?:to|and)\\b";

    private static final String SEGMENT_REGEX =
            "(?<time>\\[\\d{2}:\\d{2}:\\d{2}])"
                    + "|(?<action>\\[[A-Z_]+])"
                    + "|(?<console>\\bCONSOLE\\b)"
                    + "|(?<quoted>\"[^\"]*\")"
                    + "|(?<pdckey>[a-z0-9_.\\-]+:[a-z0-9_.\\-/]+(?==))"
                    + "|(?<key>[A-Za-z0-9_:]+(?==))"
                    + "|(?<hex>#[0-9a-fA-F]{6})"
                    + "|(?<item>\\d+x\\s+[A-Za-z0-9_' ]+?(?=\\s+(?:into|from)\\b|\\s*[{|\\]]))"
                    + "|(?<keyword>" + KEYWORD_REGEX + ")"
                    + "|(?<nameuuid>(?<![-\\w])(?<pname>[A-Za-z0-9_]{1,16}) \\((?<puuid>" + UUID_REGEX + ")\\))"
                    + "|(?<uuid>\\(?" + UUID_REGEX + "\\)?)"
                    + "|(?<punct>[=\\[\\](){}|])";

    private static final Pattern SEGMENTS = Pattern.compile(SEGMENT_REGEX);

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Object> PENDING = new ConcurrentHashMap<>();

    private LogDialog() {
    }

    public static void open(Player player, MaltsLogger logger) {
        LocalDate today = LocalDate.now();
        LogFilter filter = new LogFilter(today, today, null, null, "", "", "", "", "", "");
        applyFilter(player, logger, filter);
    }

    private static void applyFilter(Player player, MaltsLogger logger, LogFilter filter) {
        Object request = new Object();
        PENDING.put(player.getUniqueId(), request);
        Executors.runSync(player, () -> player.showDialog(buildLoading()));

        logger.query(filter)
                .thenApply(result -> new Session(logger, filter, result)) // colorize and paginate off the main thread
                .thenAccept(session -> Executors.runSync(player, () -> {
                    if (!PENDING.remove(player.getUniqueId(), request)) {
                        return; // closed while loading, or superseded by a newer query
                    }
                    SESSIONS.put(player.getUniqueId(), session);
                    show(player);
                }));
    }

    private static void showPage(Player player, int page) {
        Session session = SESSIONS.get(player.getUniqueId());
        if (session == null) {
            player.closeDialog();
            return;
        }
        int maxPage = Math.max(0, session.pages.size() - 1);
        session.page = Math.clamp(page, 0, maxPage);
        show(player);
    }

    private static void show(Player player) {
        Session session = SESSIONS.get(player.getUniqueId());
        if (session == null) {
            player.closeDialog();
            return;
        }
        player.showDialog(build(session));
    }

    private static Dialog build(Session session) {
        DialogBase base = DialogBase.builder(Component.text("Malts Logs", NamedTextColor.GOLD))
                .canCloseWithEscape(true)
                .pause(false) // required for after_action NONE
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(buildBody(session))
                .build();

        List<ActionButton> buttons = List.of(
                button("Search", "Filter by text and regular expressions", (response, player) -> openSearch(player)),
                button("Date & Time", "Filter by date and time range", (response, player) -> openDateTime(player)),
                button("Flush", "Write buffered entries to disk, then re-query",
                        (response, player) -> session.logger.flushAsync().whenComplete((ignored, error) -> reQuery(player))),
                button("< Prev", "Previous page", (response, player) -> showPage(player, session.page - 1)),
                button("Refresh", "Re-run the current filters to include newly logged entries",
                        (response, player) -> reQuery(player)),
                button("Next >", "Next page", (response, player) -> showPage(player, session.page + 1))
        );

        DialogType type = DialogType.multiAction(buttons).columns(3).exitAction(closeButton()).build();
        return Dialog.create(factory -> factory.empty().base(base).type(type));
    }

    private static Dialog buildDateTime(Session session) {
        DialogBase base = DialogBase.builder(Component.text("Malts Logs: Date & Time", NamedTextColor.GOLD))
                .canCloseWithEscape(true)
                .pause(false) // required for after_action NONE
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(List.of(DialogBody.plainMessage(
                        Component.text("Date and time range. Blank To date = same as From; blank times = ignore.",
                                NamedTextColor.GRAY), BODY_WIDTH)))
                .inputs(dateTimeInputs(session.filter))
                .build();

        List<ActionButton> buttons = List.of(
                button("Apply", "Apply this date/time range and view the results",
                        (response, player) -> applyFilter(player, session.logger, readDateTime(response, session.filter))),
                button("Back", "Return to the results without changing the filters",
                        (response, player) -> show(player))
        );

        DialogType type = DialogType.multiAction(buttons).columns(2).exitAction(closeButton()).build();
        return Dialog.create(factory -> factory.empty().base(base).type(type));
    }

    private static Dialog buildSearch(Session session) {
        DialogBase base = DialogBase.builder(Component.text("Malts Logs: Search", NamedTextColor.GOLD))
                .canCloseWithEscape(true)
                .pause(false) // required for after_action NONE
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(List.of(DialogBody.plainMessage(
                        Component.text("Filter lines by text and regex patterns. Blank = ignore.",
                                NamedTextColor.GRAY), BODY_WIDTH)))
                .inputs(searchInputs(session.filter))
                .build();

        List<ActionButton> buttons = List.of(
                button("Search", "Apply these text filters and view the results",
                        (response, player) -> applyFilter(player, session.logger, readSearch(response, session.filter))),
                button("Back", "Return to the results without changing the filters",
                        (response, player) -> show(player))
        );

        DialogType type = DialogType.multiAction(buttons).columns(2).exitAction(closeButton()).build();
        return Dialog.create(factory -> factory.empty().base(base).type(type));
    }

    private static Dialog buildLoading() {
        DialogBase base = DialogBase.builder(Component.text("Malts Logs", NamedTextColor.GOLD))
                .canCloseWithEscape(true)
                .pause(false) // required for after_action NONE
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(List.of(
                        DialogBody.plainMessage(Component.text("Searching logs...", NamedTextColor.YELLOW), BODY_WIDTH),
                        DialogBody.plainMessage(padRows(List.of(
                                Component.text("This may take a moment for large date ranges.", NamedTextColor.GRAY))), BODY_WIDTH)))
                .build();

        DialogType type = DialogType.notice(closeButton());
        return Dialog.create(factory -> factory.empty().base(base).type(type));
    }

    private static ActionButton closeButton() {
        return button("Close", "Close this window", (response, player) -> {
            PENDING.remove(player.getUniqueId());
            SESSIONS.remove(player.getUniqueId());
            player.closeDialog();
        });
    }

    private static void openDateTime(Player player) {
        Session session = SESSIONS.get(player.getUniqueId());
        if (session != null) {
            player.showDialog(buildDateTime(session));
        } else {
            player.closeDialog();
        }
    }

    private static void openSearch(Player player) {
        Session session = SESSIONS.get(player.getUniqueId());
        if (session != null) {
            player.showDialog(buildSearch(session));
        } else {
            player.closeDialog();
        }
    }

    private static void reQuery(Player player) {
        Session session = SESSIONS.get(player.getUniqueId());
        if (session != null) {
            applyFilter(player, session.logger, session.filter);
        } else {
            Executors.runSync(player, player::closeDialog);
        }
    }

    private static List<DialogBody> buildBody(Session session) {
        List<DialogBody> body = new ArrayList<>();

        if (session.result.invalidRegex()) {
            body.add(DialogBody.plainMessage(
                    Component.text("Invalid regex! Press Search, fix the pattern, and search again.", NamedTextColor.RED),
                    BODY_WIDTH));
            return body;
        }

        List<Page> pages = session.pages;

        if (pages.isEmpty()) {
            body.add(DialogBody.plainMessage(Component.text(headerText(session, null, 0), NamedTextColor.YELLOW), BODY_WIDTH));
            body.add(DialogBody.plainMessage(padRows(List.of(
                    Component.text("No matching log entries.", NamedTextColor.GRAY))), BODY_WIDTH));
            return body;
        }

        int page = Math.min(session.page, pages.size() - 1);
        Page current = pages.get(page);
        body.add(DialogBody.plainMessage(Component.text(headerText(session, current, page), NamedTextColor.YELLOW), BODY_WIDTH));
        body.add(DialogBody.plainMessage(padRows(current.rows()), BODY_WIDTH));
        return body;
    }

    private static Component padRows(List<Component> rows) {
        Component joined = Component.join(JoinConfiguration.newlines(), rows);
        for (int i = rows.size(); i < PAGE_ROWS; i++) {
            joined = joined.append(Component.newline());
        }
        return joined;
    }

    private static String headerText(Session session, Page current, int page) {
        StringBuilder sb = new StringBuilder();
        if (current == null) {
            sb.append("0 matches");
        } else {
            sb.append(current.firstLine() + 1).append('-').append(current.lastLine() + 1).append(" of ").append(compact(session.result.lines().size())).append(" matches");
        }
        if (session.result.capped()) {
            sb.append(" (capped at ").append(compact(MaltsLogger.MAX_QUERY_RESULTS)).append(')');
        }
        int pageCount = Math.max(1, session.pages.size());
        sb.append("  |  page ").append(page + 1).append('/').append(pageCount);
        if (current != null) {
            sb.append("  |  ").append(current.date());
        }
        return sb.toString();
    }

    // 5000 -> "5k", 1250 -> "1.2k" (rounded down)
    private static String compact(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        }
        int tenths = count / 100;
        return tenths % 10 == 0 ? (tenths / 10) + "k" : (tenths / 10) + "." + (tenths % 10) + "k";
    }

    private static List<Component> colorizedRows(String raw, String highlight, boolean hasHighlight) {
        StringBuilder display = new StringBuilder(raw.length());
        List<TextColor> colors = new ArrayList<>(raw.length());
        List<String> hovers = new ArrayList<>(raw.length());

        Matcher matcher = SEGMENTS.matcher(raw);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                appendRun(display, colors, hovers, raw.substring(last, matcher.start()), DEFAULT_COLOR, null);
            }
            if (matcher.group("nameuuid") != null) {
                appendRun(display, colors, hovers, matcher.group("pname"), PLAYER_COLOR, matcher.group("puuid"));
            } else {
                appendRun(display, colors, hovers, matcher.group(), colorFor(matcher, matcher.group()), null);
            }
            last = matcher.end();
        }
        if (last < raw.length()) {
            appendRun(display, colors, hovers, raw.substring(last), DEFAULT_COLOR, null);
        }

        int length = display.length();
        boolean[] highlighted = new boolean[length];
        if (hasHighlight) {
            String haystack = display.toString().toLowerCase(Locale.ROOT);
            String needle = highlight.toLowerCase(Locale.ROOT);
            for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + needle.length())) {
                for (int i = at; i < at + needle.length(); i++) {
                    highlighted[i] = true;
                }
            }
        }

        List<Component> rows = new ArrayList<>();
        for (int[] row : wrapRows(display, highlighted)) {
            rows.add(render(display, colors, hovers, highlighted, row[0], row[1]));
        }
        return rows;
    }

    private static Component render(StringBuilder display, List<TextColor> colors, List<String> hovers,
                                    boolean[] highlighted, int from, int to) {
        Component result = Component.empty();
        int i = from;
        while (i < to) {
            boolean hl = highlighted[i];
            TextColor color = colors.get(i);
            String hover = hovers.get(i);
            int j = i;
            while (j < to && highlighted[j] == hl && colors.get(j) == color && Objects.equals(hovers.get(j), hover)) {
                j++;
            }
            Component piece = Component.text(display.substring(i, j), hl ? HIGHLIGHT_COLOR : color);
            if (hl) {
                piece = piece.decorate(TextDecoration.BOLD);
            }
            if (hover != null) {
                piece = piece.hoverEvent(HoverEvent.showText(Component.text(hover, UUID_COLOR)));
            }
            result = result.append(piece);
            i = j;
        }
        return result;
    }

    private static List<int[]> wrapRows(CharSequence text, boolean[] bold) {
        List<int[]> rows = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int width = 0;
            int lastSpace = -1;
            int end = start;
            while (end < text.length()) {
                char c = text.charAt(end);
                if (c == ' ') {
                    lastSpace = end;
                }
                int charWidth = charWidth(c) + (bold[end] ? 1 : 0);
                if (end > start && width + charWidth > ROW_WIDTH) {
                    break;
                }
                width += charWidth;
                end++;
            }
            if (end == text.length()) {
                rows.add(new int[]{start, end});
                break;
            }
            if (lastSpace != -1) {
                rows.add(new int[]{start, lastSpace});
                start = lastSpace + 1;
            } else {
                rows.add(new int[]{start, end});
                start = end;
            }
        }
        if (rows.isEmpty()) {
            rows.add(new int[]{0, 0});
        }
        return rows;
    }

    private static int charWidth(char c) {
        return switch (c) {
            case '!', '\'', ',', '.', ':', ';', 'i', '|' -> 2;
            case '`', 'l' -> 3;
            case ' ', '"', '(', ')', '*', 'I', '[', ']', 't', '{', '}' -> 4;
            case '<', '>', 'f', 'k' -> 5;
            case '@', '~' -> 7;
            default -> c < 0x250 ? 6 : 9;
        };
    }

    private static void appendRun(StringBuilder display, List<TextColor> colors, List<String> hovers,
                                  String text, TextColor color, String hover) {
        display.append(text);
        for (int i = 0; i < text.length(); i++) {
            colors.add(color);
            hovers.add(hover);
        }
    }

    private static TextColor colorFor(Matcher matcher, String text) {
        if (matcher.group("time") != null) {
            return TIME_COLOR;
        }
        if (matcher.group("action") != null) {
            if (text.contains("WAREHOUSE") || text.equals("[WH]")) {
                return WAREHOUSE_COLOR;
            }
            if (text.contains("VAULT") || text.equals("[PV]")) {
                return VAULT_COLOR;
            }
            return DEFAULT_COLOR;
        }
        if (matcher.group("console") != null) {
            return CONSOLE_COLOR;
        }
        if (matcher.group("quoted") != null) {
            return QUOTED_COLOR;
        }
        if (matcher.group("pdckey") != null) {
            return PDC_KEY_COLOR;
        }
        if (matcher.group("key") != null) {
            return KEY_COLOR;
        }
        if (matcher.group("hex") != null) {
            TextColor hex = TextColor.fromHexString(text);
            return hex != null ? hex : DEFAULT_COLOR;
        }
        if (matcher.group("item") != null) {
            return ITEM_COLOR;
        }
        if (matcher.group("uuid") != null) {
            return UUID_COLOR;
        }
        if (matcher.group("keyword") != null) {
            return KEYWORD_COLOR;
        }
        if (matcher.group("punct") != null) {
            return PUNCT_COLOR;
        }
        return DEFAULT_COLOR;
    }

    private static List<DialogInput> dateTimeInputs(LogFilter filter) {
        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.text("from", Component.text("From date (YYYY-MM-DD)"))
                .initial(filter.from().format(DATE)).maxLength(10).width(200).build());
        inputs.add(DialogInput.text("to", Component.text("To date (YYYY-MM-DD)"))
                .initial(filter.to().format(DATE)).maxLength(10).width(200).build());
        inputs.add(DialogInput.text("time_from", Component.text("From time (HH:mm)"))
                .initial(orEmpty(filter.timeFrom())).maxLength(8).width(200).build());
        inputs.add(DialogInput.text("time_to", Component.text("To time (HH:mm)"))
                .initial(orEmpty(filter.timeTo())).maxLength(8).width(200).build());
        return inputs;
    }

    private static List<DialogInput> searchInputs(LogFilter filter) {
        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.text("text", Component.text("Contains text"))
                .initial(orEmpty(filter.text())).maxLength(256).width(300).build());
        inputs.add(DialogInput.text("not_text", Component.text("Does not contain text"))
                .initial(orEmpty(filter.notText())).maxLength(256).width(300).build());
        inputs.add(DialogInput.text("regex", Component.text("Matches regex"))
                .initial(orEmpty(filter.regex())).maxLength(256).width(300).build());
        inputs.add(DialogInput.text("not_regex", Component.text("Does not match regex"))
                .initial(orEmpty(filter.notRegex())).maxLength(256).width(300).build());
        inputs.add(DialogInput.text("actor", Component.text("Actor (exact name, UUID, or CONSOLE)"))
                .initial(orEmpty(filter.actor())).maxLength(256).width(300).build());
        inputs.add(DialogInput.text("owner", Component.text("Owner (exact name or UUID)"))
                .initial(orEmpty(filter.owner())).maxLength(256).width(300).build());
        return inputs;
    }

    private static ActionButton button(String label, String tooltip, Handler handler) {
        DialogAction action = DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player player) {
                handler.handle(response, player);
            }
        }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build());
        return ActionButton.builder(Component.text(label))
                .tooltip(Component.text(tooltip))
                .width(110)
                .action(action)
                .build();
    }

    private static LogFilter readDateTime(DialogResponseView response, LogFilter current) {
        LocalDate today = LocalDate.now();
        LocalDate from = parseDate(response.getText("from"));
        LocalDate to = parseDate(response.getText("to"));
        if (from == null && to == null) {
            from = today;
            to = today;
        } else if (from == null) {
            from = to;
        } else if (to == null) {
            to = from;
        }
        if (from.isAfter(to)) {
            LocalDate swap = from;
            from = to;
            to = swap;
        }
        LocalDate earliest = to.minusDays(MAX_RANGE_DAYS - 1L);
        if (from.isBefore(earliest)) {
            from = earliest;
        }

        LocalTime timeFrom = parseTime(response.getText("time_from"));
        LocalTime timeTo = parseTime(response.getText("time_to"));

        return new LogFilter(from, to, timeFrom, timeTo,
                current.text(), current.notText(), current.regex(), current.notRegex(),
                current.actor(), current.owner());
    }

    private static LogFilter readSearch(DialogResponseView response, LogFilter current) {
        String text = trimToNull(response.getText("text"));
        String notText = trimToNull(response.getText("not_text"));
        String regex = trimToNull(response.getText("regex"));
        String notRegex = trimToNull(response.getText("not_regex"));
        String actor = trimToNull(response.getText("actor"));
        String owner = trimToNull(response.getText("owner"));

        return new LogFilter(current.from(), current.to(), current.timeFrom(), current.timeTo(),
                text, notText, regex, notRegex, actor, owner);
    }

    private static LocalDate parseDate(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed);
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalTime parseTime(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return LocalTime.parse(trimmed.length() == 5 ? trimmed + ":00" : trimmed);
        } catch (Exception e) {
            return null;
        }
    }

    private static String orEmpty(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @FunctionalInterface
    private interface Handler {
        void handle(DialogResponseView response, Player player);
    }

    private record Page(LocalDate date, int firstLine, int lastLine, List<Component> rows) {
    }

    private static List<Page> buildPages(LogQueryResult result, LogFilter filter) {
        String highlight = filter.text();
        boolean hasHighlight = highlight != null && !highlight.isBlank();

        List<Page> pages = new ArrayList<>();
        List<LogQueryResult.Line> lines = result.lines();
        LocalDate date = null;
        int firstLine = 0;
        List<Component> rows = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            LogQueryResult.Line line = lines.get(i);
            if (!rows.isEmpty() && !line.date().equals(date)) {
                pages.add(new Page(date, firstLine, i - 1, rows));
                rows = new ArrayList<>();
            }
            if (rows.isEmpty()) {
                date = line.date();
                firstLine = i;
            }
            for (Component row : colorizedRows(line.text(), highlight, hasHighlight)) {
                if (rows.size() == PAGE_ROWS) {
                    pages.add(new Page(date, firstLine, i, rows));
                    rows = new ArrayList<>();
                    firstLine = i;
                }
                rows.add(row);
            }
        }
        if (!rows.isEmpty()) {
            pages.add(new Page(date, firstLine, lines.size() - 1, rows));
        }
        return pages;
    }

    private static final class Session {
        private final MaltsLogger logger;
        private final LogFilter filter;
        private final LogQueryResult result;
        private final List<Page> pages;
        private int page;

        private Session(MaltsLogger logger, LogFilter filter, LogQueryResult result) {
            this.logger = logger;
            this.filter = filter;
            this.result = result;
            this.pages = buildPages(result, filter);
        }
    }
}
