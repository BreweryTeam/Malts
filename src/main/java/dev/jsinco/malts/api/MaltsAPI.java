package dev.jsinco.malts.api;

import dev.jsinco.malts.Malts;
import dev.jsinco.malts.commands.interfaces.SubCommand;
import dev.jsinco.malts.configuration.OkaeriFile;
import dev.jsinco.malts.configuration.files.Config;
import dev.jsinco.malts.configuration.files.GuiConfig;
import dev.jsinco.malts.configuration.files.Lang;
import dev.jsinco.malts.importers.Importer;
import dev.jsinco.malts.integration.Integration;
import dev.jsinco.malts.model.MaltsPlayer;
import dev.jsinco.malts.model.SnapshotVault;
import dev.jsinco.malts.model.Vault;
import dev.jsinco.malts.model.Warehouse;
import dev.jsinco.malts.registry.Registry;
import dev.jsinco.malts.storage.DataSource;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The main entry point for the Malts API.
 * This class consists of various static methods to interact with the Malts plugin.
 * It is advised for you to read the Javadocs for each method to understand their usage.
 * For extra information, you may look into each class' source code
 *
 * <br><br/>
 * <p>
 * Events can be found in the {@link dev.jsinco.malts.api.events} package.
 * Guis can be found in the {@link dev.jsinco.malts.gui} package, although they are not technically 'exposed' API.
 * <b>When opening a Malts gui, please use the {@link dev.jsinco.malts.gui.MaltsGui#open} method to ensure proper functionality.</b>
 * </p>
 */
public final class MaltsAPI {

    private MaltsAPI() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Get the main Malts instance.
     * This is useful for accessing the plugin's logger, data folder, and other Bukkit-related functionality.
     * @return The main Malts instance
     */
    @NotNull
    public static Malts getMaltsInstance() {
        return Malts.getInstance();
    }

    /**
     * Get the config registry.
     * This registry contains all configuration files used by Malts.
     * @return The config registry
     */
    @NotNull
    public static Registry<OkaeriFile> getConfigRegistry() {
        return Registry.CONFIGS;
    }

    /**
     * Get the importer registry.
     * This registry contains all importers available for importing data from other plugins.
     * @return The importer registry
     */
    @NotNull
    public static Registry<Importer> getImporterRegistry() {
        return Registry.IMPORTERS;
    }

    /**
     * Get the sub-command registry.
     * This registry contains all sub-commands available for the /malts command.
     * @return The sub-command registry
     */
    @NotNull
    public static Registry<SubCommand> getSubCommandRegistry() {
        return Registry.SUB_COMMANDS;
    }

    /**
     * Get the integration registry.
     * This registry contains all integrations available for Malts.
     * @return The integration registry
     */
    @NotNull
    public static Registry<Integration> getIntegrationRegistry() {
        return Registry.INTEGRATIONS;
    }

    /**
     * Get the main config.
     * @return The "config.yml" file
     */
    @NotNull
    public static Config getConfig() {
        return getConfigRegistry().get(Config.class);
    }

    /**
     * Get the GUI config.
     * @return The "gui.yml" file
     */
    @NotNull
    public static GuiConfig getGuiConfig() {
        return getConfigRegistry().get(GuiConfig.class);
    }

    /**
     * Get the language config.
     * @see Lang#getFileName()
     * @return The active language file.
     */
    @NotNull
    public static Lang getLang() {
        return getConfigRegistry().get(Lang.class);
    }

    /**
     * <b>
     * Important: Some internal malts interactions are exposed through this class.
     * Using the data source directly is not recommended, and getting cacheable objects
     * directly from the database is dangerous and may lead to data inconsistency!
     * </b>
     *
     * <p>
     * Get the data source.
     * The data source is responsible for all database interactions.
     * </p>
     *
     * @return The data source
     */
    @ApiStatus.Internal
    @NotNull
    public static DataSource getDataSource() {
        return DataSource.getInstance();
    }

    /**
     * Queries the database for a vault with the given owner and id.
     * If the vault does not exist it will be created and returned.
     * @param owner The owner of the vault
     * @param id The id of the vault
     * @return A future that will complete with the vault, or complete exceptionally if not found
     */
    @NotNull
    public static CompletableFuture<@NotNull Vault> getVault(UUID owner, int id) {
        return getDataSource().getVault(owner, id);
    }

    /**
     * Queries the database for all vaults owned by the given owner.
     * These snapshot vaults can be transformed using {@link SnapshotVault#toVault()}.
     * @param owner The owner of the vaults
     * @return A future that will complete with a list of snapshot vaults, or complete exceptionally if an error occurs
     */
    @NotNull
    public static CompletableFuture<@NotNull Collection<SnapshotVault>> getVaults(UUID owner) {
        return getDataSource().getVaults(owner);
    }

    /**
     * Saves the given vault to the database.
     * @param vault The vault to save
     * @return A future that will complete when the vault is saved, or complete exceptionally if an error occurs
     */
    @NotNull
    public static CompletableFuture<@NotNull Void> saveVault(Vault vault) {
        return getDataSource().saveVault(vault);
    }

    /**
     * Deletes the vault with the given owner and id from the database.
     * @param owner The owner of the vault
     * @param id The id of the vault
     * @return A future that will complete with true if the vault was deleted, false if it did not exist, or complete exceptionally if an error occurs
     */
    @NotNull
    public static CompletableFuture<@NotNull Boolean> deleteVault(UUID owner, int id) {
        return getDataSource().deleteVault(owner, id);
    }

    /**
     * Queries the database for all vaults.
     * @return A future that will complete with a list of all vaults, or complete exceptionally if an error occurs
     */
    @NotNull
    public static CompletableFuture<@NotNull Collection<Vault>> getAllVaults() {
        return getDataSource().getAllVaults();
    }

    /**
     * Attempts to get a cached warehouse for the given owner.
     * If not cached, it will query the database and cache the result with the default expiration time set by the config.
     * @param owner The owner of the warehouse
     * @return A future that will complete with the warehouse
     */
    @NotNull
    public static CompletableFuture<@NotNull Warehouse> getOrCacheWarehouseWithDefaultExpire(UUID owner) {
        DataSource dataSource = getDataSource();
        return dataSource.cacheObjectWithDefaultExpire(dataSource.getWarehouse(owner));
    }

    /**
     * Attempts to get a cached warehouse for the given owner.
     * If not cached, it will query the database and cache the result for the number of milliseconds provided.
     * @param owner The owner of the warehouse
     * @param expireMillis The expiration time in milliseconds
     * @return A future that will complete with the warehouse
     */
    @NotNull
    public static CompletableFuture<@NotNull Warehouse> getOrCacheWarehouse(UUID owner, long expireMillis) {
        DataSource dataSource = getDataSource();
        return dataSource.cacheObject(dataSource.getWarehouse(owner), expireMillis);
    }

    /**
     * Attempts to get a cached warehouse for the given owner.
     * If not cached, it will query the database and cache the result indefinitely.
     * @see MaltsAPI#uncacheWarehouse(UUID)
     * @param owner The owner of the warehouse
     * @return A future that will complete with the warehouse
     */
    @NotNull
    public static CompletableFuture<@NotNull Warehouse> getOrCacheWarehouse(UUID owner) {
        return getDataSource().getWarehouse(owner);
    }

    /**
     * Gets a cached warehouse for the given owner.
     * If not cached, it will return null.
     * @param owner The owner of the warehouse
     * @return The cached warehouse, or null if not cached
     */
    @Nullable
    public static Warehouse getCachedWarehouse(UUID owner) {
        return getDataSource().cachedObject(owner, Warehouse.class);
    }

    /**
     * Uncaches the warehouse for the given owner and saves it to the database.
     * @param owner The owner of the warehouse
     */
    public static void uncacheWarehouse(UUID owner) { // TODO: Use CompletableFuture<Boolean>
        getDataSource().uncacheObject(owner, Warehouse.class);
    }

    /**
     * Attempts to get a cached MaltsPlayer for the given UUID.
     * If not cached, it will query the database and cache the result with the default expiration time set by the config.
     * @param uuid The UUID of the player
     * @return A future that will complete with the MaltsPlayer
     */
    @NotNull
    public static CompletableFuture<@NotNull MaltsPlayer> getOrCacheMaltsPlayerWithDefaultExpire(UUID uuid) {
        DataSource dataSource = getDataSource();
        return dataSource.cacheObjectWithDefaultExpire(dataSource.getMaltsPlayer(uuid));
    }

    /**
     * Attempts to get a cached MaltsPlayer for the given UUID.
     * If not cached, it will query the database and cache the result for the number of milliseconds provided.
     * @param uuid The UUID of the player
     * @param expireMillis The expiration time in milliseconds
     * @return A future that will complete with the MaltsPlayer
     */
    @NotNull
    public static CompletableFuture<@NotNull MaltsPlayer> getOrCacheMaltsPlayer(UUID uuid, long expireMillis) {
        DataSource dataSource = getDataSource();
        return dataSource.cacheObject(dataSource.getMaltsPlayer(uuid), expireMillis);
    }

    /**
     * Attempts to get a cached MaltsPlayer for the given UUID.
     * If not cached, it will query the database and cache the result indefinitely.
     * @see MaltsAPI#uncacheMaltsPlayer(UUID)
     * @param uuid The UUID of the player
     * @return A future that will complete with the MaltsPlayer
     */
    @NotNull
    public static CompletableFuture<@NotNull MaltsPlayer> getOrCacheMaltsPlayer(UUID uuid) {
        return getDataSource().getMaltsPlayer(uuid);
    }

    /**
     * Gets a cached MaltsPlayer for the given UUID.
     * If not cached, it will return null.
     * @param uuid The UUID of the player
     * @return The cached MaltsPlayer, or null if not cached
     */
    @Nullable
    public static MaltsPlayer getCachedMaltsPlayer(UUID uuid) {
        return getDataSource().cachedObject(uuid, MaltsPlayer.class);
    }

    /**
     * Uncaches the MaltsPlayer for the given UUID and saves it to the database.
     * @param uuid The UUID of the player
     */
    public static void uncacheMaltsPlayer(UUID uuid) { // TODO: Use CompletableFuture<Boolean>
        getDataSource().uncacheObject(uuid, MaltsPlayer.class);
    }

}
