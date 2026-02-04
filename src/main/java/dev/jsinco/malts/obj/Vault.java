package dev.jsinco.malts.obj;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.jsinco.malts.api.events.interfaces.EventAction;
import dev.jsinco.malts.api.events.vault.VaultIconChangeEvent;
import dev.jsinco.malts.api.events.vault.VaultNameChangeEvent;
import dev.jsinco.malts.api.events.vault.VaultOpenEvent;
import dev.jsinco.malts.api.events.vault.VaultTrustPlayerEvent;
import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.files.Config;
import dev.jsinco.malts.configuration.files.Lang;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.utility.Couple;
import dev.jsinco.malts.utility.Executors;
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
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a transient vault inventory in Malts.
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
    private static final Config cfg = ConfigManager.get(Config.class);

    @Getter
    private final VaultKey key;
    private final Inventory inventory;
    @NotNull
    private String customName;
    @NotNull
    private Material icon;
    private List<UUID> trustedPlayers;

    public Vault(UUID owner, int id) {
        Preconditions.checkArgument(id > 0, "Vault ID must be greater than 0");
        this.key = VaultKey.of(owner, id);
        this.customName = cfg.vaults().defaultName().replace("{id}", String.valueOf(id));
        this.icon = cfg.vaults().defaultIcon();
        this.trustedPlayers = new ArrayList<>();

        int size = cfg.vaults().size();

        this.inventory = Bukkit.createInventory(this, size, Text.mm(customName));
    }

    public Vault(UUID owner, int id, ItemStack[] items) {
        Preconditions.checkArgument(id > 0, "Vault ID must be greater than 0");
        this.key = VaultKey.of(owner, id);
        this.customName = cfg.vaults().defaultName().replace("{id}", String.valueOf(id));
        this.icon = cfg.vaults().defaultIcon();
        this.trustedPlayers = new ArrayList<>();


        int count = items != null ? items.length : 9;
        int size = Math.max(((count + 8) / 9) * 9, cfg.vaults().size());
        this.inventory = Bukkit.createInventory(this, size, Text.mm(customName));
        if (items != null) {
            inventory.setContents(items);
        }
    }

    public Vault(UUID owner, int id, String encodedInventory, String customName, Material icon, String trustedPlayers) {
        this.key = VaultKey.of(owner, id);
        this.customName = customName != null && !customName.isEmpty() ? customName : "Vault #" + id;
        this.icon = icon != null && icon.isItem() ? icon : cfg.vaults().defaultIcon();

        List<UUID> json = GSON.fromJson(trustedPlayers, LIST_UUID_TYPE_TOKEN);
        this.trustedPlayers = json != null ? json : new ArrayList<>();

        ItemStack[] items = null;
        if (encodedInventory != null && !encodedInventory.isEmpty()) {
            items = decodeInventory(encodedInventory);
        }

        int count = items != null ? items.length : 9;
        int size = Math.max(((count + 8) / 9) * 9, cfg.vaults().size());
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
        Executors.runSync(() -> {
            List<Player> viewers = this.getViewers();
            boolean canOpen = this.canOpen(player, viewers);
            VaultOpenEvent event = new VaultOpenEvent(this, player, viewers, !Bukkit.isPrimaryThread());
            event.setCancelled(!canOpen);
            Text.debug("Player " + player.getName() + " is attempting to open vault " + this.key.id() + ". Can open: " + canOpen);

            if (!event.callEvent()) {
                ConfigManager.get(Lang.class).entry(l -> l.vaults().alreadyOpen(), player);
                return;
            }

            player.openInventory(this.inventory);



            if (viewers.isEmpty()) {
                return;
            }

            Player otherPlayer = viewers.getFirst();
            if (otherPlayer.getOpenInventory().getTopInventory().getHolder(false) instanceof Vault otherVault) {
                otherVault.update(otherPlayer); // Update this vault
            }
        });
    }


    private boolean canOpen(Player player, List<Player> viewers) {
        // A vault can be opened by a player if:
        // - No other players are viewing it
        // - The player has the bypass permission
        // - The only other viewers are players with the bypass permission
        // A vault may never be opened if it is locked.
        DataSource dataSource = DataSource.getInstance();
        if (dataSource.isLocked(this.key)) {
            Text.debug("Vault " + this.key.id() + " is locked, player " + player.getName() + " cannot open it.");
            return false;
        }

        if (viewers.isEmpty()) {
            Text.debug("Vault " + this.key.id() + " has no viewers, player " + player.getName() + " can open it.");
            return true;
        }

        if (player.hasPermission(BYPASS_OPEN_VAULT_PERM)) {
            Text.debug("Player " + player.getName() + " has bypass permission, can open vault " + this.key.id() + " despite viewers.");
            return true;
        }

        for (Player viewer : viewers) {
            // If any viewer does not have bypass permission or is the owner, deny access
            if (!viewer.hasPermission(BYPASS_OPEN_VAULT_PERM) || viewer.getUniqueId().equals(this.key.owner())) {
                return false;
            }
        }
        Text.debug("All viewers of vault " + this.key.id() + " have bypass permission, player " + player.getName() + " can open it.");
        return true;
    }

    public List<Player> getViewers() {
        List<Player> viewers = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (this.equals(player.getOpenInventory().getTopInventory().getHolder(false))) {
                viewers.add(player);
            }
        }
        return viewers;
    }

    /**
     * Malts holds no references to open vaults.
     * This method updates all open inventories of this vault for all players except the updater.
     * This method runs synchronously, avoid superfluous calls.
     * 
     * @param updater the player who initiated the update, may be null
     */
    public void update(@Nullable Player updater) {
        Executors.delayedSync(1, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (updater != null && player.getUniqueId() == updater.getUniqueId()) {
                    continue;
                }
                
                Inventory inv = player.getOpenInventory().getTopInventory();
                if (this.equals(inv.getHolder(false))) {
                    inv.setContents(this.inventory.getContents());
                    Text.debug("Updated inventory for player " + player.getName() + " with vault " + this.key.id());
                }
            }
        });
    }


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
        int cap = cfg.vaults().trustCap();
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
        int maxLength = cfg.vaults().maxNameCharacters();
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
        copy.getInventory().setContents(this.getInventory().getContents());
        return copy;
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

    public enum VaultOpenState {
        OPEN,
        BYPASSED,
        CLOSED;
    }
}
