package dev.jsinco.malts.model;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.jsinco.malts.api.events.interfaces.EventAction;
import dev.jsinco.malts.api.events.vault.VaultIconChangeEvent;
import dev.jsinco.malts.api.events.vault.VaultNameChangeEvent;
import dev.jsinco.malts.api.events.vault.VaultTrustPlayerEvent;
import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.files.Config;
import dev.jsinco.malts.configuration.files.Lang;
import dev.jsinco.malts.utility.Text;
import dev.jsinco.malts.utility.Util;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a transient vault inventory in Malts.
 *
 * <p>
 * Please note: Unlike other objects in malts, vaults are not cached in memory. While a vault is open,
 * its contents are managed by a VaultSession until the last viewer closes it, and it has been saved.
 * </p>
 *
 * @see dev.jsinco.malts.storage.DataSource#getVault(UUID, int) 
 * @see dev.jsinco.malts.storage.DataSource#saveVault(Vault) 
 */
@Getter
@Setter
public class Vault implements MaltsInventory {

    public static final Type LIST_UUID_TYPE_TOKEN = new TypeToken<List<UUID>>(){}.getType();
    public static String BYPASS_OPEN_VAULT_PERM = "malts.bypass.openvault";

    private static final Gson GSON = Util.GSON;
    private static final Config CONFIG = ConfigManager.get(Config.class);
    private static final Lang LANG = ConfigManager.get(Lang.class);

    @Getter
    private final VaultKey key;
    private final Inventory inventory;
    @NotNull
    private String customName;
    @NotNull
    private Material icon;
    private List<UUID> trustedPlayers;
    private boolean locked;

    public Vault(UUID owner, int id) {
        Preconditions.checkArgument(id > 0, "Vault ID must be greater than 0");
        this.key = VaultKey.of(owner, id);
        this.customName = CONFIG.vaults().defaultName().replace("{id}", String.valueOf(id));
        this.icon = CONFIG.vaults().defaultIcon();
        this.trustedPlayers = new ArrayList<>();

        int size = CONFIG.vaults().size();

        this.inventory = Bukkit.createInventory(this, size, Text.mm(customName));
    }

    public Vault(UUID owner, int id, ItemStack[] items) {
        Preconditions.checkArgument(id > 0, "Vault ID must be greater than 0");
        this.key = VaultKey.of(owner, id);
        this.customName = CONFIG.vaults().defaultName().replace("{id}", String.valueOf(id));
        this.icon = CONFIG.vaults().defaultIcon();
        this.trustedPlayers = new ArrayList<>();


        int count = items != null ? items.length : 9;
        int size = Math.max(((count + 8) / 9) * 9, CONFIG.vaults().size());
        this.inventory = Bukkit.createInventory(this, size, Text.mm(customName));
        if (items != null) {
            inventory.setContents(items);
        }
    }

    public Vault(UUID owner, int id, String encodedInventory, String customName, Material icon, String trustedPlayers, boolean locked) {
        this.key = VaultKey.of(owner, id);
        this.locked = locked;
        this.customName = customName != null && !customName.isEmpty() ? customName : "Vault #" + id;
        this.icon = icon != null && icon.isItem() ? icon : CONFIG.vaults().defaultIcon();

        List<UUID> json = GSON.fromJson(trustedPlayers, LIST_UUID_TYPE_TOKEN);
        this.trustedPlayers = json != null ? json : new ArrayList<>();

        ItemStack[] items = null;
        if (encodedInventory != null && !encodedInventory.isEmpty()) {
            items = decodeInventory(encodedInventory);
        }

        int count = items != null ? items.length : 9;
        int size = Math.max(((count + 8) / 9) * 9, CONFIG.vaults().size());
        this.inventory = Bukkit.createInventory(this, size, Text.mm(customName));
        if (items != null) {
            inventory.setContents(items);
        }
    }

    public String encodeInventory() {
        byte[] itemByteArray = ItemStack.serializeItemsAsBytes(inventory.getContents());
        return Base64.getEncoder().encodeToString(itemByteArray);
    }

    public String encodeTrusted() {
        return GSON.toJson(trustedPlayers, LIST_UUID_TYPE_TOKEN);
    }

    /**
     * Opens this vault for the specified player running normal Malts checks
     * such as the open state and API events.
     * 
     * @param player the player to open the vault for
     */
    public void open(Player player) {
        VaultSessions.open(player, this);
    }


    public State canOpen(Player player, List<Player> viewers) {
        // A vault can be opened by a player if:
        // - No other players are viewing it
        // - Shared viewing is enabled
        // - The player has the bypass permission
        // - The only other viewers are players with the bypass permission
        if (viewers.isEmpty()) {
            Text.debug("Vault " + this.key.id() + " has no viewers, player " + player.getName() + " can open it.");
            return State.AVAILABLE;
        }

        if (CONFIG.vaults().sharedViewing()) {
            return State.AVAILABLE;
        }

        if (player.hasPermission(BYPASS_OPEN_VAULT_PERM)) {
            Text.debug("Player " + player.getName() + " has bypass permission, can open vault " + this.key.id() + " despite viewers.");
            return State.BYPASS_OPEN_PERMISSION;
        }

        for (Player viewer : viewers) {
            // If any viewer does not have bypass permission or is the owner, deny access
            if (!viewer.hasPermission(BYPASS_OPEN_VAULT_PERM) || viewer.getUniqueId().equals(this.key.owner())) {
                return State.OTHER_PLAYER_VIEWING;
            }
        }
        Text.debug("All viewers of vault " + this.key.id() + " have bypass permission, player " + player.getName() + " can open it.");
        return State.AVAILABLE;
    }


    public CompletableFuture<State> canOpen(Player player) {
        return this.getViewers().thenApply(viewers -> canOpen(player, viewers));
    }


    public CompletableFuture<List<Player>> getViewers() {
        VaultSession session = VaultSessions.get(this.key);
        return CompletableFuture.completedFuture(session != null ? session.viewerPlayers(null) : new ArrayList<>());
    }


    /**
     * Checks to see if the player has the proper permissions to access this vault.
     * This does not mean the vault is openable, only that the player has access rights.
     * @param player the player to check
     * @return true if the player can access the vault, false otherwise
     */
    public boolean canAccess(Player player) {
        return player.getUniqueId() == this.key.owner() || this.trustedPlayers.contains(player.getUniqueId()) || player.hasPermission(BYPASS_OPEN_VAULT_PERM);
    }

    public boolean isTrusted(UUID uuid) {
        return trustedPlayers.contains(uuid) || uuid == this.key.owner();
    }

    /**
     * Adds a trusted player to this vault.
     * @param uuid the UUID of the player to trust
     * @return true if the player was added, false if the player was already trusted or the trust cap was reached
     */
    public boolean addTrusted(UUID uuid) {
        int cap = CONFIG.vaults().trustCap();
        VaultTrustPlayerEvent event = new VaultTrustPlayerEvent(this, EventAction.ADD, uuid, !Bukkit.isPrimaryThread());
        event.setCancelled(trustedPlayers.size() >= cap);

        if (!event.callEvent()) {
            return false;
        }
        trustedPlayers.add(event.getTrustedUUID());
        return true;
    }

    /**
     * Removes a trusted player from this vault.
     * @param uuid the UUID of the player to untrust
     * @return true if the player was removed, false if the player was not trusted
     */
    public boolean removeTrusted(UUID uuid) {
        if (!trustedPlayers.contains(uuid)) return false;
        VaultTrustPlayerEvent event = new VaultTrustPlayerEvent(this, EventAction.REMOVE, uuid, !Bukkit.isPrimaryThread());
        if (!event.callEvent()) return false;

        trustedPlayers.remove(event.getTrustedUUID());
        return true;
    }

    /**
     * Sets the custom name of this vault.
     * @param customName the new custom name
     * @return true if the name was set, false if the name was too long
     */
    public boolean setCustomName(@NotNull String customName) {
        int maxLength = CONFIG.vaults().maxNameCharacters();
        VaultNameChangeEvent event = new VaultNameChangeEvent(this, customName, !Bukkit.isPrimaryThread());
        event.setCancelled(customName.length() > maxLength);
        if (!event.callEvent()) return false;
        customName = event.getNewName();

        if (customName.length() > maxLength) {
            return false;
        }

        this.customName = customName;
        return true;
    }

    public void forceSetCustomName(@NotNull String customName) {
        this.customName = customName;
    }

    /**
     * Sets the icon of this vault.
     * @param icon the new icon
     * @return true if the icon was set, false otherwise
     */
    public boolean setIcon(@NotNull Material icon) {
        VaultIconChangeEvent event = new VaultIconChangeEvent(this, icon, !Bukkit.isPrimaryThread());
        if (!event.callEvent()) return false;

        this.icon = event.getNewIcon();
        return true;
    }

    public void forceSetIcon(@NotNull Material icon) {
        this.icon = icon;
    }

    /**
     * Creates a copy of this vault for a new owner.
     * @param newOwner the UUID of the new owner
     * @return a copy of this vault
     */
    public Vault copy(UUID newOwner) {
        Vault copy = new Vault(newOwner, this.key.id());
        copy.setCustomName(this.customName);
        copy.setIcon(this.icon);
        copy.setTrustedPlayers(new ArrayList<>(this.trustedPlayers));
        copy.setLocked(this.locked);
        copy.getInventory().setContents(this.getInventory().getContents());
        return copy;
    }

    /**
     * Saves only this vault's metadata and applies it to any open copies, so editing a vault
     * while someone has it open neither reverts their items nor gets reverted when they close it.
     */
    public CompletableFuture<Void> saveMeta() {
        return VaultSessions.acquire(this.key).thenCompose(session -> {
            try {
                session.applyMeta(this);
                return session.save();
            } finally {
                session.release();
            }
        });
    }

    void copyMetaFrom(Vault other) {
        this.customName = other.customName;
        this.icon = other.icon;
        this.trustedPlayers = new ArrayList<>(other.trustedPlayers);
        this.locked = other.locked;
    }

    Vault snapshot() {
        return new Vault(this);
    }

    private Vault(Vault source) {
        this.key = source.key;
        this.customName = source.customName;
        this.icon = source.icon;
        this.trustedPlayers = new ArrayList<>(source.trustedPlayers);
        this.locked = source.locked;
        this.inventory = Bukkit.createInventory(this, source.inventory.getSize(), Text.mm(this.customName));
        this.inventory.setContents(VaultSession.cloneContents(source.inventory.getContents()));
    }

    /**
     * @return true if this vault is locked and locking is enabled in the config
     */
    public boolean isLockActive() {
        return this.locked && CONFIG.vaults().lockingEnabled();
    }

    public UUID getOwner() {
        return key.owner();
    }

    public int getId() {
        return key.id();
    }

    private static ItemStack[] decodeInventory(String encodedInventory) {
        byte[] itemByteArray = Base64.getDecoder().decode(encodedInventory);
        return ItemStack.deserializeItemsFromBytes(itemByteArray);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Vault vault = (Vault) o;
        return Objects.equals(this.key, vault.key);
    }

    @Override
    public int hashCode() {
        return this.key.hashCode();
    }

    public enum State {
        AVAILABLE, // true
        DATABASE_BUSY, // false
        OTHER_PLAYER_VIEWING, // false
        BYPASS_OPEN_PERMISSION; // true

        /**
         * Converts the state to a boolean indicating if the vault can be opened.
         * @return true if the vault can be opened, false otherwise
         */
        public boolean toBoolean() {
            return this == AVAILABLE || this == BYPASS_OPEN_PERMISSION;
        }
    }
}
