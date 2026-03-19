package dev.jsinco.malts.storage.sources;

import com.zaxxer.hikari.HikariConfig;
import dev.jsinco.malts.configuration.files.Config;
import dev.jsinco.malts.model.MaltsPlayer;
import dev.jsinco.malts.model.SnapshotVault;
import dev.jsinco.malts.model.Stock;
import dev.jsinco.malts.model.Vault;
import dev.jsinco.malts.model.Warehouse;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.utility.Executors;
import dev.jsinco.malts.utility.Text;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("DuplicatedCode") // TODO: Extract out common code
public class MariaDBDataSource extends DataSource {

    public MariaDBDataSource(Config.Storage config) {
        super(config);
    }

    @Override
    public HikariConfig hikariConfig(Config.Storage config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("MaltsMariaDB");
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikariConfig.setJdbcUrl("jdbc:mysql://" + config.host() + ":" + config.port() + "/" + config.database() + config.jdbcFlags());
        hikariConfig.setUsername(config.username());
        hikariConfig.setPassword(config.password());
        hikariConfig.setMaximumPoolSize(10);
        return hikariConfig;
    }

    @Override
    public CompletableFuture<Void> createTables() {
        return Executors.supplyAsyncWithSQLException(() -> {
            try (Connection connection = this.connection()) {
                for (String statement : this.getStatements("tables/mysql/create_tables.sql")) {
                    connection.prepareStatement(statement).execute();
                }
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Vault> getVault(UUID owner, int id, boolean createIfNull) {
        return Executors.supplyAsyncWithSQLException(() -> {
            try (Connection connection = this.connection()) {
                PreparedStatement statement = connection.prepareStatement(
                        this.getStatement("vaults/select_vault.sql")
                );
                statement.setString(1, owner.toString());
                statement.setInt(2, id);
                ResultSet resultSet = statement.executeQuery();
                return this.mapVault(resultSet, owner, id, createIfNull);
            }
        });
    }

    @Override
    public CompletableFuture<@Nullable Vault> getVault(UUID owner, String customName) {
        return Executors.supplyAsyncWithSQLException(() -> {
            try (Connection connection = this.connection()) {
                PreparedStatement statement = connection.prepareStatement(
                        this.getStatement("vaults/select_vault_by_name.sql")
                );
                statement.setString(1, owner.toString());
                statement.setString(2, customName);
                ResultSet resultSet = statement.executeQuery();

                return this.mapVault(resultSet, owner);
            }
        });
    }

    @Override
    public CompletableFuture<@NotNull Collection<SnapshotVault>> getVaults(UUID owner) {
        return Executors.supplyAsyncWithSQLException(() -> {
            try (Connection connection = this.connection()) {
                PreparedStatement statement = connection.prepareStatement(
                        this.getStatement("vaults/select_owned_vaults.sql")
                );
                statement.setString(1, owner.toString());
                ResultSet resultSet = statement.executeQuery();
                return this.mapSnapshotVaults(resultSet, owner);
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveVault(Vault vault) {
        return Executors.supplyAsyncWithSQLException(() -> {
            try (Connection connection = this.connection()) {
                PreparedStatement statement = connection.prepareStatement(
                        this.getStatement("vaults/mysql/insert_or_update_vault.sql")
                );
                statement.setString(1, vault.getOwner().toString());
                statement.setInt(2, vault.getId());
                statement.setString(3, vault.encodeInventory());
                statement.setString(4, vault.getCustomName());
                statement.setString(5, vault.getIcon().toString());
                statement.setString(6, vault.encodeTrusted());
                statement.executeUpdate();
                Text.debug("Saved vault: " + vault.getOwner() + " #" + vault.getId());
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteVault(UUID owner, int id) {
        return Executors.supplyAsyncWithSQLException(() -> {
            try (Connection connection = this.connection()) {
                PreparedStatement statement = connection.prepareStatement(
                        this.getStatement("vaults/delete_vault.sql")
                );
                statement.setString(1, owner.toString());
                statement.setInt(2, id);
                int rowsAffected = statement.executeUpdate();


                Text.debug("Attempted to delete vault: " + owner + " #" + id);
                return rowsAffected > 0;
            }
        });
    }

    @Override
    public CompletableFuture<@NotNull Integer> deleteVaults(UUID owner) {
        return Executors.supplyAsyncWithSQLException(() -> {
            try (Connection connection = this.connection()) {
                PreparedStatement statement = connection.prepareStatement(
                        this.getStatement("vaults/delete_all_vaults.sql")
                );
                statement.setString(1, owner.toString());
                int rowsAffected = statement.executeUpdate();
                Text.debug("Deleted all vaults for owner: " + owner);
                return rowsAffected;
            }
        });
    }

    @Override
    public CompletableFuture<@NotNull Collection<Vault>> getAllVaults() {
        return Executors.supplyAsyncWithSQLException(() -> {
            try (Connection connection = this.connection()) {
                PreparedStatement statement = connection.prepareStatement(
                        this.getStatement("vaults/select_all_vaults.sql")
                );

                ResultSet resultSet = statement.executeQuery();
                return this.mapVaults(resultSet);
            }
        });
    }

    @Override
    public CompletableFuture<List<String>> getVaultNames(UUID owner) {
        return Executors.supplyAsyncWithSQLException(() -> {
            try (Connection connection = this.connection()) {
                PreparedStatement statement = connection.prepareStatement(
                        this.getStatement("vaults/select_vault_names.sql")
                );

                statement.setString(1, owner.toString());

                ResultSet resultSet = statement.executeQuery();
                List<String> vaultNames = new ArrayList<>();
                while (resultSet.next()) {
                    String name = resultSet.getString("normalized_name");
                    vaultNames.add(name);
                }
                return vaultNames;
            }
        });
    }

    @Override
    public CompletableFuture<@NotNull Warehouse> getWarehouse(UUID owner) {
        return Executors.supplyAsyncWithSQLException(() -> {
            try (Connection connection = this.connection()) {

                PreparedStatement warehouseStatement = connection.prepareStatement(
                        this.getStatement("warehouses/select_warehouse.sql")
                );

                warehouseStatement.setString(1, owner.toString());

                ResultSet resultSet = warehouseStatement.executeQuery();
                return this.mapWarehouse(resultSet, owner);
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveWarehouse(Warehouse warehouse) {
        return Executors.supplyAsyncWithSQLException(() -> {
            try (Connection connection = this.connection()) {
                UUID owner = warehouse.getOwner();
                Map<Material, Stock> map = warehouse.stock();

                // Execute INSERT/UPDATE
                try (PreparedStatement ps = connection.prepareStatement(
                        this.getStatement("warehouses/mysql/insert_or_update_warehouse.sql")
                )) {
                    for (Map.Entry<Material, Stock> entry : map.entrySet()) {
                        ps.setString(1, owner.toString());
                        ps.setString(2, entry.getKey().name());
                        ps.setInt(3, entry.getValue().getAmount());
                        ps.setLong(4, entry.getValue().getLastUpdate());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                // Execute DELETE for removed items
                if (map.isEmpty()) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            this.getStatement("warehouses/delete_all_warehouse.sql")
                    )) {
                        ps.setString(1, owner.toString());
                        ps.executeUpdate();
                    }
                } else {
                    String placeholders = String.join(", ", Collections.nCopies(map.size(), "?"));
                    String deleteSql = this.getStatement("warehouses/delete_stale_warehouse.sql")
                            .replace("(?)", "(" + placeholders + ")");

                    try (PreparedStatement ps = connection.prepareStatement(deleteSql)) {
                        ps.setString(1, owner.toString());
                        int i = 2;
                        for (Material m : map.keySet()) {
                            ps.setString(i++, m.name());
                        }
                        ps.executeUpdate();
                    }
                }
            }
            Text.debug("Saved warehouse: " + warehouse.getOwner());
            return null;
        });
    }

    @Override
    public CompletableFuture<@NotNull MaltsPlayer> getMaltsPlayer(UUID uuid) {
        return Executors.supplyAsyncWithSQLException(() -> {
            try (Connection connection = this.connection()) {
                PreparedStatement statement = connection.prepareStatement(
                        this.getStatement("players/select_player.sql")
                );
                statement.setString(1, uuid.toString());
                ResultSet resultSet = statement.executeQuery();
                return this.mapMaltsPlayer(resultSet, uuid);
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveMaltsPlayer(MaltsPlayer maltsPlayer) {
        return Executors.supplyAsyncWithSQLException(() -> {
            try (Connection connection = this.connection()) {
                PreparedStatement statement = connection.prepareStatement(
                        this.getStatement("players/mysql/insert_or_update_player.sql")
                );
                statement.setString(1, maltsPlayer.getUuid().toString());
                statement.setInt(2, maltsPlayer.getMaxVaults());
                statement.setInt(3, maltsPlayer.getMaxWarehouseStock());
                statement.setString(4, maltsPlayer.getWarehouseMode().name());
                statement.setString(5, maltsPlayer.getQuickReturnClickType().name());
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Integer> getTotalVaultCount() {
        return Executors.supplyAsyncWithSQLException(() -> {
            try (Connection connection = this.connection()) {
                PreparedStatement statement = connection.prepareStatement(
                        this.getStatement("vaults/total_vault_count.sql")
                );
                ResultSet rs = statement.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        });
    }

    @Override
    public CompletableFuture<Integer> getTotalWarehouseStockCount() {
        return Executors.supplyAsyncWithSQLException(() -> {
            try (Connection connection = this.connection()) {
                PreparedStatement statement = connection.prepareStatement(
                        this.getStatement("warehouses/total_warehouse_stock.sql")
                );
                ResultSet rs = statement.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        });
    }
}
