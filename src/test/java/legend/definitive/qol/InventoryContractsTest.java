package legend.definitive.qol;

import legend.core.GameEngine;
import legend.core.tags.StringTag;
import legend.game.inventory.Inventory;
import legend.game.inventory.Item;
import legend.game.inventory.ItemIcon;
import legend.game.inventory.ItemRegistryEvent;
import legend.game.inventory.ItemStack;
import legend.game.modding.coremod.CoreMod;
import legend.game.saves.ConfigRegistryEvent;
import legend.game.saves.ConfigCollection;
import legend.game.saves.ConfigStorage;
import legend.game.saves.ConfigStorageLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Registry and storage only: no game assets, engine launch, SDL window or device input. */
class InventoryContractsTest {
  @BeforeAll static void registry() {
    CoreMod.registerConfig(new ConfigRegistryEvent((legend.game.saves.ConfigRegistry)GameEngine.REGISTRIES.config));
    CoreMod.registerItems(new ItemRegistryEvent((legend.game.inventory.ItemRegistry)GameEngine.REGISTRIES.items));
  }

  private static Item item(final int stackSize) {
    return new Item(ItemIcon.SWORD, 10) {
      @Override public int getMaxStackSize(final ItemStack stack) { return stackSize; }
      @Override public boolean canBeUsed(final ItemStack stack, final UsageLocation location) { return true; }
      @Override public boolean canTarget(final ItemStack stack, final TargetType type) { return true; }
    };
  }

  @Test void quantityPreviewKeepsOriginalBagAndSingleItemStackRules() {
    final Inventory inventory = new Inventory();
    inventory.disableEvents();
    inventory.setMaxSize(3);
    final Item potion = item(1);
    inventory.give(potion);
    assertEquals(2, ShopQuantity.itemCapacity(inventory, new ItemStack(potion)));
    assertEquals(1, inventory.getSize());
    assertEquals(1, inventory.get(0).getSize());
    inventory.give(potion);
    inventory.give(potion);
    assertEquals(0, ShopQuantity.itemCapacity(inventory, new ItemStack(potion)));
  }

  @Test void quantityPreviewUsesExistingStackRoomWithoutRaisingLimits() {
    final Inventory inventory = new Inventory();
    inventory.disableEvents();
    inventory.setMaxSize(1);
    final Item potion = item(5);
    inventory.give(new ItemStack(potion, 2));
    assertEquals(3, ShopQuantity.itemCapacity(inventory, new ItemStack(potion)));
    assertEquals(2, inventory.get(0).getSize());
  }

  @Test void saleGroupingDoesNotMixModDataOrDurability() {
    final Inventory inventory = new Inventory();
    inventory.disableEvents();
    inventory.setMaxSize(5);
    final Item potion = item(1);
    inventory.give(potion);
    inventory.give(potion);
    inventory.give(item(1));
    assertEquals(2, ShopQuantity.saleCount(inventory, inventory.get(0)));
    inventory.get(0).setExtraData(new StringTag("custom"));
    assertEquals(1, ShopQuantity.saleCount(inventory, inventory.get(0)));
    assertFalse(ShopQuantity.sameForSale(inventory.get(0), inventory.get(1)));
  }

  @Test void purchasedStackRetainsHookMutations() {
    final Inventory inventory = new Inventory();
    inventory.disableEvents();
    inventory.setMaxSize(2);
    final ItemStack template = new ItemStack(item(1));
    final StringTag marker = new StringTag("purchase hook");
    assertTrue(ShopQuantity.purchaseItem(inventory, template, 10, () -> 20,
      unit -> unit.setExtraData(marker)));
    assertEquals(marker.get(), ((StringTag)inventory.get(0).getExtraData()).get());
    assertNull(template.getExtraData());
    assertFalse(ShopQuantity.purchaseItem(inventory, template, 10, () -> 0, unit -> { }));
    assertEquals(1, inventory.getSize());
  }

  @Test void saleMaximumExcludesProtectedMatchingStacks() {
    final Inventory inventory = new Inventory();
    inventory.disableEvents();
    inventory.setMaxSize(2);
    final Item protectedItem = new Item(ItemIcon.SWORD, 10) {
      @Override public boolean isProtected(final ItemStack stack) { return stack.getSize() > 1; }
      @Override public boolean canBeUsed(final ItemStack stack, final UsageLocation location) { return true; }
      @Override public boolean canTarget(final ItemStack stack, final TargetType type) { return true; }
    };
    inventory.give(new ItemStack(protectedItem));
    inventory.give(new ItemStack(protectedItem, 2));
    assertEquals(1, ShopQuantity.saleCount(inventory, inventory.get(0)));
  }

  @Test void integratedSettingsPersistPerCampaignWithoutLeakingAcrossCampaigns(@TempDir final Path directory) throws Exception {
    final ConfigCollection first = new ConfigCollection(false);
    final ConfigCollection second = new ConfigCollection(false);
    first.setConfig(CoreMod.ENEMY_XP_MULTIPLIER_CONFIG.get(), 1.75f);
    first.setConfig(CoreMod.ENEMY_GOLD_MULTIPLIER_CONFIG.get(), 0.25f);
    first.setConfig(CoreMod.ADDITION_FEEDBACK_CONFIG.get(), true);
    first.setConfig(CoreMod.SHOP_QUANTITIES_CONFIG.get(), false);
    first.setConfig(CoreMod.EQUIPMENT_SORT_CONFIG.get(), EquipmentSort.POWER);
    final Path config = directory.resolve("campaign_config.dcnf");
    ConfigStorage.saveConfig(first, ConfigStorageLocation.CAMPAIGN, config);
    assertTrue(java.nio.file.Files.size(config) > 0);
    final ConfigCollection restored = new ConfigCollection(false);
    ConfigStorage.loadConfig(restored, ConfigStorageLocation.CAMPAIGN, config);
    assertEquals(1.75f, restored.getConfig(CoreMod.ENEMY_XP_MULTIPLIER_CONFIG.get()));
    assertEquals(0.25f, restored.getConfig(CoreMod.ENEMY_GOLD_MULTIPLIER_CONFIG.get()));
    assertTrue(restored.getConfig(CoreMod.ADDITION_FEEDBACK_CONFIG.get()));
    assertFalse(restored.getConfig(CoreMod.SHOP_QUANTITIES_CONFIG.get()));
    assertEquals(EquipmentSort.POWER, restored.getConfig(CoreMod.EQUIPMENT_SORT_CONFIG.get()));
    assertEquals(1.0f, second.getConfig(CoreMod.ENEMY_XP_MULTIPLIER_CONFIG.get()));
    assertEquals(1.0f, second.getConfig(CoreMod.ENEMY_GOLD_MULTIPLIER_CONFIG.get()));
    assertFalse(second.getConfig(CoreMod.ADDITION_FEEDBACK_CONFIG.get()));
  }
}
