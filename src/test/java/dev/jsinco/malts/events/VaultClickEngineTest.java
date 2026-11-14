package dev.jsinco.malts.events;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.DragType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockBukkitExtension.class)
class VaultClickEngineTest {

    private static final ClickType[] VAULT_CLICKS = {
            ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT,
            ClickType.NUMBER_KEY, ClickType.SWAP_OFFHAND, ClickType.DOUBLE_CLICK
    };
    private static final ClickType[] PLAYER_CLICKS = {
            ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT, ClickType.DOUBLE_CLICK
    };
    private static final Material[] MATERIALS = {
            Material.DIAMOND, Material.STONE, Material.ENDER_PEARL, Material.DIAMOND_SWORD
    };

    @MockBukkitInject
    ServerMock server;
    PlayerMock player;
    Inventory vault;

    @BeforeEach
    void setUp() {
        VaultClickEngine.placeholderCheck = item -> false;
        player = server.addPlayer("Tester");
        vault = server.createInventory(null, 54);
    }

    @Test
    void leftClickPicksUpAndPlaces() {
        vault.setItem(0, new ItemStack(Material.DIAMOND, 10));
        VaultClickEngine.clickVault(player, vault, false, 0, ClickType.LEFT, -1);
        assertEquals(10, player.getItemOnCursor().getAmount());
        assertNull(vault.getItem(0));

        VaultClickEngine.clickVault(player, vault, false, 5, ClickType.RIGHT, -1);
        assertEquals(1, vault.getItem(5).getAmount());
        assertEquals(9, player.getItemOnCursor().getAmount());
    }

    @Test
    void shiftClickMovesIntoHotbarFromTheRight() {
        vault.setItem(3, new ItemStack(Material.STONE, 64));
        VaultClickEngine.clickVault(player, vault, false, 3, ClickType.SHIFT_LEFT, -1);
        assertNull(vault.getItem(3));
        assertEquals(64, player.getInventory().getItem(8).getAmount());
    }

    @Test
    void evenDragSplitsCursor() {
        player.setItemOnCursor(new ItemStack(Material.DIAMOND, 9));
        VaultClickEngine.drag(player, vault, false, List.of(
                new VaultClickEngine.DragTarget(true, 0),
                new VaultClickEngine.DragTarget(true, 1),
                new VaultClickEngine.DragTarget(false, 9)
        ), DragType.EVEN);
        assertEquals(3, vault.getItem(0).getAmount());
        assertEquals(3, vault.getItem(1).getAmount());
        assertEquals(3, player.getInventory().getItem(9).getAmount());
        assertEquals(0, player.getItemOnCursor().getAmount());
    }

    // No sequence of clicks may ever create or destroy items
    @Test
    void randomClicksConserveItems() {
        Random random = new Random(42);
        for (int round = 0; round < 200; round++) {
            vault.clear();
            player.getInventory().clear();
            player.setItemOnCursor(null);
            fill(random, vault, vault.getSize());
            fill(random, player.getInventory(), 41);
            Map<Material, Integer> expected = totals();

            for (int step = 0; step < 300; step++) {
                int op = random.nextInt(10);
                if (op < 6) {
                    ClickType click = VAULT_CLICKS[random.nextInt(VAULT_CLICKS.length)];
                    VaultClickEngine.clickVault(player, vault, false, random.nextInt(vault.getSize()), click, random.nextInt(9));
                } else if (op < 8) {
                    ClickType click = PLAYER_CLICKS[random.nextInt(PLAYER_CLICKS.length)];
                    VaultClickEngine.clickPlayer(player, vault, false, random.nextInt(36), click);
                } else {
                    List<VaultClickEngine.DragTarget> targets = new ArrayList<>();
                    int count = 1 + random.nextInt(6);
                    for (int i = 0; i < count; i++) {
                        boolean inVault = random.nextBoolean();
                        targets.add(new VaultClickEngine.DragTarget(inVault, random.nextInt(inVault ? vault.getSize() : 36)));
                    }
                    VaultClickEngine.drag(player, vault, false, targets, random.nextBoolean() ? DragType.EVEN : DragType.SINGLE);
                }
                assertEquals(expected, totals(), "Items changed in round " + round + " step " + step);
            }
        }
    }

    private static void fill(Random random, Inventory inventory, int slots) {
        for (int i = 0; i < slots; i++) {
            if (random.nextInt(3) == 0) {
                Material material = MATERIALS[random.nextInt(MATERIALS.length)];
                inventory.setItem(i, new ItemStack(material, 1 + random.nextInt(material.getMaxStackSize())));
            }
        }
    }

    private Map<Material, Integer> totals() {
        Map<Material, Integer> totals = new EnumMap<>(Material.class);
        List<ItemStack> all = new ArrayList<>();
        all.addAll(Arrays.asList(vault.getContents()));
        all.addAll(Arrays.asList(player.getInventory().getContents()));
        all.add(player.getItemOnCursor());
        for (ItemStack item : all) {
            if (item != null && !item.isEmpty()) {
                totals.merge(item.getType(), item.getAmount(), Integer::sum);
            }
        }
        return totals;
    }
}
