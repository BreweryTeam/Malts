package dev.jsinco.malts.storage;

import com.google.common.base.Preconditions;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.jsinco.malts.Malts;
import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.files.Config;
import dev.jsinco.malts.configuration.files.Lang;
import dev.jsinco.malts.enums.QuickReturnClickType;
import dev.jsinco.malts.enums.TriState;
import dev.jsinco.malts.enums.WarehouseMode;
import dev.jsinco.malts.integration.EconomyIntegration;
import dev.jsinco.malts.model.CachedObject;
import dev.jsinco.malts.model.MaltsPlayer;
import dev.jsinco.malts.model.SnapshotVault;
import dev.jsinco.malts.model.Stock;
import dev.jsinco.malts.model.Vault;
import dev.jsinco.malts.model.VaultKey;
import dev.jsinco.malts.model.Warehouse;
import dev.jsinco.malts.utility.Couple;
import dev.jsinco.malts.utility.Executors;
import dev.jsinco.malts.utility.FileUtil;
import dev.jsinco.malts.utility.Text;
import dev.jsinco.malts.utility.Util;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public abstract class DataSource {

    public static final Path DATA_FOLDER = Malts.getInstance().getDataPath();
    private static final int SAVE_INTERVAL_SECONDS = 60;
    private static final int TASK_INTERVAL_SECONDS = 2;

    @Getter
    private static DataSource instance;

    protected final ExecutorService singleThread = Executors.newSingleThreadExecutor();
    private final ConcurrentLinkedQueue<CachedObject> cachedObjects = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<VaultKey, Object> vaultLocks = new ConcurrentHashMap<>();

    @Getter
    private final HikariDataSource hikari;
    private ScheduledTask cacheTask;

    public abstract HikariConfig hikariConfig(Config.Storage config);
    public abstract CompletableFuture<Void> createTables();

    public abstract CompletableFuture<@Nullable Vault> getVault(UUID owner, int id, boolean createIfNull);
    public abstract CompletableFuture<@Nullable Vault> getVault(UUID owner, String customName);

    public abstract CompletableFuture<@NotNull Collection<SnapshotVault>> getVaults(UUID owner);
    public abstract CompletableFuture<Void> saveVault(Vault vault);
    public abstract CompletableFuture<@NotNull Boolean> deleteVault(UUID owner, int id);
    public abstract CompletableFuture<@NotNull Integer> deleteVaults(UUID owner);
    public abstract CompletableFuture<@NotNull Collection<Vault>> getAllVaults(); // Used for exporting vaults. (Mainly to other plugins)
    public abstract CompletableFuture<@NotNull List<String>> getVaultNames(UUID owner);


    public abstract CompletableFuture<@NotNull Warehouse> getWarehouse(UUID owner);
    public abstract CompletableFuture<Void> saveWarehouse(Warehouse warehouse);

    public abstract CompletableFuture<@NotNull MaltsPlayer> getMaltsPlayer(UUID uuid);
    public abstract CompletableFuture<Void> saveMaltsPlayer(MaltsPlayer maltsPlayer);

    // Metrics
    public abstract CompletableFuture<Integer> getTotalVaultCount();
    public abstract CompletableFuture<Integer> getTotalWarehouseStockCount();

    // Abstract Utility

    protected abstract CompletableFuture<Void> saveCachedObjects(Collection<CachedObject> objects);

    public CompletableFuture<@NotNull Vault> getVault(UUID owner, int id) {
        return getVault(owner, id, true);
    }

    public void getVaultWithEconomy(Player player, int id, Consumer<@NotNull Vault> consumer) {
        Config.Economy economy = ConfigManager.get(Config.class).economy();
        Lang lang = ConfigManager.get(Lang.class);
        @Nullable EconomyIntegration econ = economy.economyProvider().getIntegration();
        double creationFee = economy.vaults().creationFee();
        double accessFee = economy.vaults().accessFee();
        UUID owner = player.getUniqueId();

        getVault(owner, id, false).thenAccept(vault -> {
            if (vault == null) { // Check if vault exists
                // Check if we're using economy and if there's a creation fee
                if (econ != null && !econ.withdrawOrBypass(player, creationFee)) {
                    lang.entry(l -> l.economy().vaults().cannotAffordCreation(), player, Couple.of("{cost}", String.format("%,.2f", creationFee)));
                    return; // Cannot afford creation
                }

                // Create the vault since it doesn't exist
                vault = new Vault(owner, id);
                if (econ != null) { // Charge creation fee
                    lang.entry(l -> l.economy().vaults().created(), player, Couple.of("{cost}", String.format("%,.2f", creationFee)));
                }
            } else if (econ != null) { // Existing vault, charge access fee
                if (!econ.withdrawOrBypass(player, accessFee)) {
                    lang.entry(l -> l.economy().vaults().cannotAffordAccess(), player, Couple.of("{cost}", String.format("%,.2f", accessFee)));
                    return; // Cannot afford access fee
                }
                lang.entry(l -> l.economy().vaults().accessed(), player, Couple.of("{cost}", String.format("%,.2f", accessFee)));
            }

            consumer.accept(vault); // Done, provide the vault
        });
    }



    public DataSource(Config.Storage config) {
        HikariConfig hikariConfig = hikariConfig(config);

        hikariConfig.setMaxLifetime(1800000); // 30 min
        hikariConfig.setIdleTimeout(600000); // 10 min
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setKeepaliveTime(300000); // 5 min
        hikariConfig.setConnectionTestQuery("SELECT 1");
        this.hikari = new HikariDataSource(hikariConfig);
    }

    public Connection connection() throws SQLException {
        return hikari.getConnection();
    }

    public CompletableFuture<Void> setup() {
        AtomicInteger count = new AtomicInteger(0);
        return this.createTables().thenRun(() -> this.cacheTask = Executors.runRepeatingAsync(TASK_INTERVAL_SECONDS, TimeUnit.SECONDS, task -> {
            int intervalCount = count.getAndAdd(TASK_INTERVAL_SECONDS);
            boolean shouldSave = intervalCount >= SAVE_INTERVAL_SECONDS;

            List<CachedObject> toSave = new ArrayList<>();
            List<CachedObject> toRemove = new ArrayList<>();

            for (CachedObject cachedObject : cachedObjects) {
                if (cachedObject.isExpired()) {
                    toSave.add(cachedObject);
                    toRemove.add(cachedObject);
                } else if (shouldSave) {
                    toSave.add(cachedObject);
                }
            }

            if (!toSave.isEmpty()) {
                Text.debug("Cached Objects size: " + cachedObjects.size());
                saveCachedObjects(toSave).thenRun(() -> {
                    for (CachedObject obj : toRemove) {
                        cachedObjects.remove(obj);
                        Text.debug("Uncached " + obj.getClass().getSimpleName() + ": " + obj.getUuid() + " because it was expired");
                    }
                });
            }

            if (shouldSave) {
                count.set(0);
            }
        }));
    }

    public String[] getStatements(String path) {
        String[] statements = FileUtil.readInternalResource("sql/" + path).split(";");
        // re-append the semicolon to each statement
        for (int i = 0; i < statements.length; i++) {
            statements[i] = statements[i].trim() + ";";
        }
        return statements;
    }

    public String getStatement(String path) {
        return FileUtil.readInternalResource("sql/" + path);
    }

    public CompletableFuture<Void> clearCache() {
        List<CachedObject> snapshot = new ArrayList<>(cachedObjects);
        return saveCachedObjects(snapshot).thenRun(cachedObjects::clear);
    }

    // TODO: Better logging
    public CompletableFuture<Void> close() {
        // Wait for all saves to complete, then close hikari
        return clearCache()
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    return null;
                })
                .thenRun(() -> {
                    cacheTask.cancel();
                    hikari.close();
                    singleThread.shutdown();
                });
    }

    public TriState isClosed() {
        boolean hikariClosed = hikari.isClosed();
        boolean singleThreadClosed = singleThread.isShutdown();
        boolean cacheTaskClosed = cacheTask.isCancelled();
        if (hikariClosed && singleThreadClosed && cacheTaskClosed) {
            return TriState.TRUE;
        } else if (hikariClosed || singleThreadClosed || cacheTaskClosed) {
            return TriState.ALTERNATIVE_STATE;
        } else {
            return TriState.FALSE;
        }
    }

    @Nullable
    public Vault mapVault(ResultSet rs, UUID owner, int id, boolean create) throws SQLException {
        if (rs.next()) {
            return new Vault(
                    owner,
                    id,
                    rs.getString("inventory"),
                    rs.getString("custom_name"),
                    Material.getMaterial(rs.getString("icon")),
                    rs.getString("trusted_players")
            );
        }
        return create ? new Vault(owner, id) : null;
    }

    @Nullable
    public Vault mapVault(ResultSet rs, UUID owner) throws SQLException {
        if (rs.next()) {
            return new Vault(
                    owner,
                    rs.getInt("id"),
                    rs.getString("inventory"),
                    rs.getString("custom_name"),
                    Material.getMaterial(rs.getString("icon")),
                    rs.getString("trusted_players")
            );
        }
        return null;
    }

    public List<Vault> mapVaults(ResultSet rs) throws SQLException {
        List<Vault> vaults = new ArrayList<>();
        while (rs.next()) {
            vaults.add(
                    new Vault(
                            UUID.fromString(rs.getString("owner")),
                            rs.getInt("id"),
                            rs.getString("inventory"),
                            rs.getString("custom_name"),
                            Material.getMaterial(rs.getString("icon")),
                            rs.getString("trusted_players")
                    )
            );
        }
        return vaults;
    }

    public List<SnapshotVault> mapSnapshotVaults(ResultSet rs, UUID owner) throws SQLException {
        List<SnapshotVault> vaults = new ArrayList<>();
        while (rs.next()) {
            vaults.add(
                    new SnapshotVault(
                            owner,
                            rs.getInt("id"),
                            rs.getString("custom_name"),
                            Material.getMaterial(rs.getString("icon")),
                            rs.getString("trusted_players")
                    )
            );
        }
        return vaults;
    }

    public MaltsPlayer mapMaltsPlayer(ResultSet rs, UUID uuid) throws SQLException {
        if (rs.next()) {
            int maxVaults = rs.getInt("max_vaults");
            int maxWarehouseStock = rs.getInt("max_warehouse_stock");
            WarehouseMode warehouseMode = Util.getEnum(rs.getString("warehouse_mode"), WarehouseMode.class);
            QuickReturnClickType quickReturnClickType = Util.getEnum(rs.getString("quick_return_click_type"), QuickReturnClickType.class);
            return new MaltsPlayer(uuid, maxVaults, maxWarehouseStock, warehouseMode, quickReturnClickType);
        }
        return new MaltsPlayer(uuid);
    }

    public Warehouse mapWarehouse(ResultSet rs, UUID uuid) throws SQLException {
        EnumMap<Material, Stock> warehouseMap = new EnumMap<>(Material.class);

        while (rs.next()) {
            String mstring = rs.getString("material");

            Material material = Material.matchMaterial(mstring);
            int quantity = rs.getInt("quantity");
            long lastUpdate = rs.getLong("last_update");

            if (material == null) {
                throw new RuntimeException("Material " + mstring + " does not exist");
            }
            warehouseMap.put(material, new Stock(material, quantity, lastUpdate));
        }

        return new Warehouse(uuid, warehouseMap);
    }


    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends CachedObject> T cachedObject(UUID uuid, Class<T> objectClass) {
        Preconditions.checkNotNull(uuid, "uuid cannot be null");
        Preconditions.checkNotNull(objectClass, "objectClass cannot be null");


        return (T) cachedObjects.stream().filter(it ->
            it.getClass().equals(objectClass) && it.getUuid().equals(uuid)
        ).findFirst().orElse(null);
    }

    public <T extends CachedObject> void cacheAlreadyCompleted(T object, long expire) {
        Preconditions.checkNotNull(object, "object cannot be null");
        CompletableFuture<T> future = CompletableFuture.completedFuture(object);
        cacheObjectInternal(future, expire);
    }

    public <T extends CachedObject> CompletableFuture<T> cacheObjectWithDefaultExpire(CompletableFuture<T> future) {
        long expire = ConfigManager.get(Config.class).storage().defaultObjectCacheTime();
        return cacheObject(future, expire);
    }

    public <T extends CachedObject> CompletableFuture<T> cacheObject(CompletableFuture<T> future, long expire) {
        return cacheObjectInternal(future, expire);
    }

    public <T extends CachedObject> CompletableFuture<T> cacheObject(CompletableFuture<T> future) {
        return cacheObjectInternal(future, null);
    }

    private <T extends CachedObject> CompletableFuture<T> cacheObjectInternal(CompletableFuture<T> future, Long expireTime) {
        return future.thenApply(obj -> {  // thenApply instead of thenCompose — no async submission here
            if (obj == null) {
                return null;
            }

            synchronized (cachedObjects) {
                for (CachedObject cached : cachedObjects) {
                    if (cached.getClass().equals(obj.getClass()) &&
                            cached.getUuid().equals(obj.getUuid())) {
                        @SuppressWarnings("unchecked")
                        T alreadyCached = (T) cached;
                        if (expireTime != null) {
                            long expireWhen = System.currentTimeMillis() + expireTime;
                            alreadyCached.setExpire(expireWhen);
                        }
                        Text.debug("Using cached " + obj.getClass().getSimpleName() + ": " + obj.getUuid());
                        return alreadyCached;
                    }
                }
            }

            // Not found, cache the new object
            if (expireTime != null) {
                long expireWhen = System.currentTimeMillis() + expireTime;
                obj.setExpire(expireWhen);
            }

            synchronized (cachedObjects) {
                cachedObjects.add(obj);
                //new CachedObjectEvent(this, obj, EventAction.ADD).callEvent();
            }

            String expireMsg = obj.getExpire() != null ? " until " + obj.getExpire() : "";
            Text.debug("Caching " + obj.getClass().getSimpleName() + ": " + obj.getUuid() + expireMsg);
            return obj;
        });
    }


    public void uncacheObject(UUID uuid, Class<? extends CachedObject> objectClass) {
        CachedObject cachedObject = cachedObject(uuid, objectClass);
        if (cachedObject != null) {
            Text.debug("Uncaching " + cachedObject.getClass().getSimpleName() + ": " + cachedObject.getUuid());
            cachedObject.save(this);
            cachedObjects.remove(cachedObject);
            //new CachedObjectEvent(this, cachedObject, EventAction.REMOVE).callEvent();
        }
    }


    public boolean isLocked(VaultKey key) {
        return vaultLocks.containsKey(key);
    }

    public void lock(VaultKey key) {
        vaultLocks.put(key, new Object());
    }

    public void releaseLock(VaultKey key) {
        vaultLocks.remove(key);
    }



    @Override
    public String toString() {
        return "DataSource{" +
                "singleThread=" + singleThread +
                ", hikari=" + hikari +
                ", cacheTask=" + cacheTask +
                ", cachedObjects=" + cachedObjects +
                '}';
    }

    public static void createInstance() {
        Config config = new ConfigManager().craft(Config.class);
        createInstance(config.storage());
    }

    public static void createInstance(Config.Storage config) throws IllegalStateException {
        TriState closed = instance != null ? instance.isClosed() : TriState.TRUE;
        if (closed != TriState.TRUE) {
            throw new IllegalStateException(closed == TriState.ALTERNATIVE_STATE ? "DataSource is not properly closed." : "DataSource is not closed.");
        }

        instance = config.driver().supply(config); // Set a new instance
        instance.setup().whenComplete((unused, throwable) -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                instance.cacheObject(instance.getMaltsPlayer(uuid));
                instance.cacheObject(instance.getWarehouse(uuid));
            }
        }).exceptionally(ex -> {
            throw new IllegalStateException("An exception/error occurred while setting up the DataSource", ex);
        });
    }
}
