package dev.jsinco.malts.logging;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogLinesTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 12);

    private static LogLines.Parsed rendered(LocalTime time, LogAction action, String message) {
        String raw = new LogEntry(LocalDateTime.of(DATE, time), action, message).render();
        return LogLines.parse(DATE, raw);
    }

    private static boolean matches(LogLines.Parsed line, LogFilter filter) {
        return LogLines.matches(line, filter, LogLines.compile(filter));
    }

    private static LogFilter search(String text, String notText, String regex, String notRegex) {
        return new LogFilter(DATE, DATE, null, null, text, notText, regex, notRegex, null, null);
    }

    private static LogFilter actorOwner(String actor, String owner) {
        return new LogFilter(DATE, DATE, null, null, null, null, null, null, actor, owner);
    }

    private static LogFilter timeWindow(LocalTime from, LocalTime to) {
        return new LogFilter(DATE, DATE, from, to, null, null, null, null, null, null);
    }

    @Test
    void renderUsesCategoryPrefixAndParseReadsItsTime() {
        String raw = new LogEntry(LocalDateTime.of(DATE, LocalTime.of(14, 30, 0)), LogAction.WAREHOUSE_STOCK,
                "Steve (id) stocked 5x Diamond into warehouse").render();
        assertTrue(raw.contains("[WH]"), "warehouse actions log the [WH] category prefix, not the action name");
        LogLines.Parsed parsed = LogLines.parse(DATE, raw);
        assertEquals(LocalTime.of(14, 30, 0), parsed.time());
    }

    @Test
    void parseLeavesActionNullForCategoryLines() {
        LogLines.Parsed parsed = LogLines.parse(DATE, "[09:00:00] [WH] Steve stocked 3x Stone into warehouse");
        assertEquals(LocalTime.of(9, 0, 0), parsed.time());
        assertNull(parsed.action(), "[WH]/[PV] category prefixes carry no resolvable action");
    }

    @Test
    void containsTextIsCaseInsensitive() {
        LogLines.Parsed line = rendered(LocalTime.NOON, LogAction.VAULT_EDIT_NAME, "Steve renamed vault 3");
        assertTrue(matches(line, search("RENAMED", null, null, null)));
        assertFalse(matches(line, search("untrusted", null, null, null)));
    }

    @Test
    void doesNotContainTextExcludesMatchingLines() {
        LogLines.Parsed line = rendered(LocalTime.NOON, LogAction.WAREHOUSE_STOCK, "Steve stocked 5x Diamond into warehouse");
        assertFalse(matches(line, search(null, "diamond", null, null)), "line containing the excluded text must be dropped");
        assertTrue(matches(line, search(null, "emerald", null, null)));
    }

    @Test
    void matchesRegexAndDoesNotMatchRegexAreOpposites() {
        LogLines.Parsed line = rendered(LocalTime.NOON, LogAction.VAULT_DELETE, "Admin deleted vault 42");
        assertTrue(matches(line, search(null, null, "vault \\d+", null)));
        assertFalse(matches(line, search(null, null, null, "vault \\d+")), "not-regex must exclude a matching line");
    }

    @Test
    void allTextFiltersAreAppliedTogether() {
        LogLines.Parsed line = rendered(LocalTime.NOON, LogAction.WAREHOUSE_STOCK, "Steve stocked 5x Diamond into warehouse");
        assertTrue(matches(line, search("diamond", "emerald", "\\[WH]", "\\[PV]")));
        assertFalse(matches(line, search("diamond", "warehouse", "\\[WH]", "\\[PV]")));
    }

    @Test
    void actorFilterMatchesNameUuidAndConsole() {
        LogLines.Parsed steve = rendered(LocalTime.NOON, LogAction.VAULT_EDIT_ICON,
                "Steve (ec568111-1f7f-4446-90e5-095eac5cc9cb) changed icon of vault 1 from Anvil to Command Block");
        assertTrue(matches(steve, actorOwner("steve", null)), "actor filter matches the actor name");
        assertTrue(matches(steve, actorOwner("ec568111-1f7f-4446-90e5-095eac5cc9cb", null)),
                "actor filter matches the actor UUID");
        assertFalse(matches(steve, actorOwner("alex", null)), "actor filter excludes other actors");
        assertFalse(matches(steve, actorOwner("stev", null)), "actor filter is exact, not a substring match");

        LogLines.Parsed console = rendered(LocalTime.NOON, LogAction.VAULT_EDIT_ICON,
                "CONSOLE changed icon of vault 1 from Anvil to Command Block "
                        + "(owner=Mitalityyy (ec568111-1f7f-4446-90e5-095eac5cc9cb))");
        assertTrue(matches(console, actorOwner("console", null)), "actor filter matches the CONSOLE actor");
    }

    @Test
    void ownerFilterUsesOwnerSuffixAndFallsBackToActor() {
        LogLines.Parsed withOwner = rendered(LocalTime.NOON, LogAction.VAULT_EDIT_ICON,
                "CONSOLE changed icon of vault 1 from Anvil to Command Block "
                        + "(owner=Mitalityyy (ec568111-1f7f-4446-90e5-095eac5cc9cb))");
        assertTrue(matches(withOwner, actorOwner(null, "mitalityyy")), "owner filter matches the owner name");
        assertTrue(matches(withOwner, actorOwner(null, "ec568111-1f7f-4446-90e5-095eac5cc9cb")),
                "owner filter matches the owner UUID");
        assertFalse(matches(withOwner, actorOwner(null, "console")),
                "owner filter does not match the actor when a distinct owner is present");

        LogLines.Parsed selfOwned = rendered(LocalTime.NOON, LogAction.VAULT_EDIT_NAME,
                "Steve (ec568111-1f7f-4446-90e5-095eac5cc9cb) renamed vault 3 from \"a\" to \"b\"");
        assertTrue(matches(selfOwned, actorOwner(null, "steve")),
                "owner falls back to the actor when there is no (owner=...) suffix");
    }

    @Test
    void timeWindowIsInclusiveOnBothEnds() {
        LogLines.Parsed line = rendered(LocalTime.of(14, 30, 0), LogAction.WAREHOUSE_STOCK, "Steve stocked");
        assertTrue(matches(line, timeWindow(LocalTime.of(14, 30, 0), LocalTime.of(14, 30, 0))));
        assertFalse(matches(line, timeWindow(LocalTime.of(14, 31, 0), null)));
        assertFalse(matches(line, timeWindow(null, LocalTime.of(14, 29, 0))));
    }
}
