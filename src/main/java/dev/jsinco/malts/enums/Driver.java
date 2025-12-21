package dev.jsinco.malts.enums;

import dev.jsinco.malts.configuration.files.Config;
import dev.jsinco.malts.storage.sources.MariaDBDataSource;
import lombok.Getter;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.storage.sources.SQLiteDataSource;
import org.jetbrains.annotations.Nullable;

@Getter
public enum Driver {

    SQLITE(SQLiteDataSource::new, SQLiteDataSource.class, "SQLite"),
    MARIADB(MariaDBDataSource::new, MariaDBDataSource.class, "MariaDB", "MySQL");

    private final DriverSupplier supplier;
    private final Class<? extends DataSource> identifyingClass;
    private final String[] names;

    Driver(DriverSupplier supplier, Class<? extends DataSource> identifyingClass, String... names) {
        this.supplier = supplier;
        this.identifyingClass = identifyingClass;
        this.names = names;
    }

    @Override
    public String toString() {
        return names[0];
    }

    @SuppressWarnings("unchecked")
    public <T extends DataSource> T supply(Config.Storage config) {
        return (T) supplier.supply(config);
    }

    @Nullable
    public static Driver fromName(String name) {
        for (Driver driver : values()) {
            for (String driverName : driver.getNames()) {
                if (driverName.equalsIgnoreCase(name)) {
                    return driver;
                }
            }
        }
        return null;
    }

    public interface DriverSupplier {
        DataSource supply(Config.Storage config);
    }
}
