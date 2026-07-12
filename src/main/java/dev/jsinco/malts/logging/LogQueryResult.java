package dev.jsinco.malts.logging;

import java.time.LocalDate;
import java.util.List;

public record LogQueryResult(List<Line> lines, int totalMatches, boolean capped, boolean invalidRegex) {

    public record Line(LocalDate date, String text) {
    }

    public static LogQueryResult ofInvalidRegex() {
        return new LogQueryResult(List.of(), 0, false, true);
    }
}
