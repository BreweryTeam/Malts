package dev.jsinco.malts.logging;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public record LogEntry(LocalDateTime timestamp, LogAction action, String message) {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public LogEntry(LogAction action, String message) {
        this(LocalDateTime.now(), action, message);
    }

    public String render() {
        LocalTime time = timestamp.toLocalTime();
        return "[" + TIME_FORMAT.format(time) + "] " + action.category().prefix() + " " + message;
    }
}
