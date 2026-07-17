package dev.jsinco.malts.logging;

public enum LogAction {

    WAREHOUSE_ADD(LogCategory.WAREHOUSE),
    WAREHOUSE_REMOVE(LogCategory.WAREHOUSE),
    WAREHOUSE_STOCK(LogCategory.WAREHOUSE),
    WAREHOUSE_DESTOCK(LogCategory.WAREHOUSE),
    WAREHOUSE_MODE(LogCategory.WAREHOUSE),
    VAULT_DEPOSIT(LogCategory.VAULT),
    VAULT_WITHDRAW(LogCategory.VAULT),
    VAULT_EDIT_ICON(LogCategory.VAULT),
    VAULT_EDIT_NAME(LogCategory.VAULT),
    VAULT_EDIT_TRUST(LogCategory.VAULT),
    VAULT_TRANSFER(LogCategory.VAULT),
    VAULT_DELETE(LogCategory.VAULT);

    private final LogCategory category;

    LogAction(LogCategory category) {
        this.category = category;
    }

    public LogCategory category() {
        return category;
    }
}
