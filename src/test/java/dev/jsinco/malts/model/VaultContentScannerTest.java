package dev.jsinco.malts.model;

import dev.jsinco.malts.Malts;
import dev.jsinco.malts.utility.ItemStacks;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// TODO: Unimplemented methods in mockbukkit
@ExtendWith(MockBukkitExtension.class)
class VaultContentScannerTest {

    @MockBukkitInject
    ServerMock serverMock;
    PlayerMock mockPlayer;
    List<Vault> mockVaults;

    @BeforeEach
    void setUp() {
        MockBukkit.load(Malts.class);

        this.mockPlayer = this.serverMock.addPlayer("TestPlayer");
        this.mockVaults = List.of(
                new Vault(mockPlayer.getUniqueId(), 1),
                new Vault(mockPlayer.getUniqueId(), 2),
                new Vault(mockPlayer.getUniqueId(), 3)
        );
        Inventory inv = this.mockVaults.getFirst().getInventory();
        inv.addItem(ItemStacks.builder().material(Material.DIAMOND_BLOCK).amount(64).build());
        inv.addItem(ItemStacks.builder().material(Material.GOLD_BLOCK).amount(32).build());

    }

    //@Test Can't test this, unimplemented methods in MockBukkit
    void testVaultScanResultsSize() {
        int page = 1;
        VaultContentScanner scanner = new VaultContentScanner(this.mockVaults, page, null);
        VaultContentScanner.ResultCollection results = scanner.matchingVaults("gold");
        assertEquals(1, results.getResults().size(), "Should find 1 vault with gold");

//        assertNotNull(foundVault, "Vault should be found");
//        assertEquals(2, foundVault.getId(), "Found vault should have ID 2");
    }

}