package dev.jsinco.malts.logging;

public enum LogAction {

    VAULT_DEPOSIT(LogCategory.VAULT),
    VAULT_WITHDRAW(LogCategory.VAULT),
    WAREHOUSE_STOCK(LogCategory.WAREHOUSE),
    WAREHOUSE_DESTOCK(LogCategory.WAREHOUSE);

    private final LogCategory category;

    LogAction(LogCategory category) {
        this.category = category;
    }

    public LogCategory category() {
        return category;
    }
}
