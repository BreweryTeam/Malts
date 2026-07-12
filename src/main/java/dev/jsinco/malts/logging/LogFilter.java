package dev.jsinco.malts.logging;

import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalTime;

public record LogFilter(
        LocalDate from,
        LocalDate to,
        @Nullable LocalTime timeFrom,
        @Nullable LocalTime timeTo,
        @Nullable String text,
        @Nullable String notText,
        @Nullable String regex,
        @Nullable String notRegex
) {
}
