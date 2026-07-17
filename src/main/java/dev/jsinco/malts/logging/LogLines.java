package dev.jsinco.malts.logging;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LogLines {

    static final Pattern LINE_PATTERN = Pattern.compile("^\\[(\\d{2}:\\d{2}:\\d{2})]\\s+(?:\\[([A-Z_]+)]\\s+)?(.*)$");

    private static final String UUID_REGEX =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private static final Pattern ACTOR_PATTERN = Pattern.compile(
            "^(?:\\[\\d{2}:\\d{2}:\\d{2}]\\s+)?(?:\\[[A-Z_]+]\\s+)?"
                    + "([A-Za-z0-9_]{1,16} \\(" + UUID_REGEX + "\\)|" + UUID_REGEX + "|\\S+)");

    private static final Pattern OWNER_PATTERN = Pattern.compile("\\(owner=(.*)\\)$");

    private static final Pattern REF_PATTERN = Pattern.compile("^(.*) \\((" + UUID_REGEX + ")\\)$");

    private LogLines() {
    }

    static Parsed parse(LocalDate date, String raw) {
        Matcher matcher = LINE_PATTERN.matcher(raw);
        LocalTime time = null;
        LogAction action = null;
        if (matcher.matches()) {
            try {
                time = LocalTime.parse(matcher.group(1));
            } catch (Exception ignored) {
            }
            String actionName = matcher.group(2);
            if (actionName != null) {
                try {
                    action = LogAction.valueOf(actionName);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return new Parsed(date, time, action, raw);
    }

    static Compiled compile(LogFilter filter) {
        return new Compiled(
                pattern(filter.regex()),
                pattern(filter.notRegex()),
                lower(filter.text()),
                lower(filter.notText()),
                lower(filter.actor()),
                lower(filter.owner()));
    }

    static boolean matches(Parsed line, LogFilter filter, Compiled compiled) {
        if (filter.timeFrom() != null && (line.time() == null || line.time().isBefore(filter.timeFrom()))) {
            return false;
        }
        if (filter.timeTo() != null && (line.time() == null || line.time().isAfter(filter.timeTo()))) {
            return false;
        }

        String raw = line.raw();
        String rawLower = null;
        if (compiled.textLower() != null) {
            rawLower = raw.toLowerCase(Locale.ROOT);
            if (!rawLower.contains(compiled.textLower())) {
                return false;
            }
        }
        if (compiled.notTextLower() != null) {
            if (rawLower == null) {
                rawLower = raw.toLowerCase(Locale.ROOT);
            }
            if (rawLower.contains(compiled.notTextLower())) {
                return false;
            }
        }
        if (compiled.regex() != null && !compiled.regex().matcher(raw).find()) {
            return false;
        }
        if (compiled.notRegex() != null && compiled.notRegex().matcher(raw).find()) {
            return false;
        }

        if (compiled.actorLower() != null || compiled.ownerLower() != null) {
            String actor = actorOf(raw);
            if (compiled.actorLower() != null && !refEquals(actor, compiled.actorLower())) {
                return false;
            }
            if (compiled.ownerLower() != null && !refEquals(ownerOf(raw, actor), compiled.ownerLower())) {
                return false;
            }
        }
        return true;
    }

    private static boolean refEquals(String ref, String filterLower) {
        Matcher matcher = REF_PATTERN.matcher(ref);
        String name = matcher.matches() ? matcher.group(1) : ref;
        String uuid = matcher.matches() ? matcher.group(2) : ref;
        return filterLower.equals(name.toLowerCase(Locale.ROOT))
                || filterLower.equals(uuid.toLowerCase(Locale.ROOT));
    }

    private static String actorOf(String raw) {
        Matcher matcher = ACTOR_PATTERN.matcher(raw);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String ownerOf(String raw, String actor) {
        Matcher matcher = OWNER_PATTERN.matcher(raw);
        return matcher.find() ? matcher.group(1) : actor;
    }

    private static Pattern pattern(String value) {
        return value == null || value.isBlank() ? null : Pattern.compile(value);
    }

    private static String lower(String value) {
        return value == null || value.isBlank() ? null : value.toLowerCase(Locale.ROOT);
    }

    record Parsed(LocalDate date, LocalTime time, LogAction action, String raw) {
    }

    record Compiled(Pattern regex, Pattern notRegex, String textLower, String notTextLower,
                    String actorLower, String ownerLower) {
    }
}
