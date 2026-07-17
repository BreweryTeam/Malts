package dev.jsinco.malts.logging;

public enum LogCategory {

    WAREHOUSE("[WH]"),
    VAULT("[PV]");

    private final String prefix;

    LogCategory(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
