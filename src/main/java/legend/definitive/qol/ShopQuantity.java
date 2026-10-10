package legend.definitive.qol;

import legend.game.inventory.Inventory;
import legend.game.inventory.ItemStack;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class ShopQuantity {
  public static final int MAX_QUANTITY = 99;

  private ShopQuantity() { }

  public static int addGold(final int current, final int amount) {
    return (int)Math.clamp((long)current + amount, 0L, 99_999_999L);
  }

  public static int affordable(final int gold, final int unitPrice, final int capacity) {
    if(unitPrice < 0 || gold < 0) return 0;
    return Math.clamp(unitPrice == 0 ? capacity : Math.min(capacity, gold / unitPrice), 0, MAX_QUANTITY);
  }

  /** Simulate ordinary insertion, respecting stack sizes, durability and occupied slots. */
  public static int itemCapacity(final Inventory inventory, final ItemStack unit) {
    if(unit.isEmpty()) return 0;
    final Inventory preview = new Inventory(inventory);
    preview.disableEvents();
    int count = 0;
    while(count < MAX_QUANTITY && preview.give(unit.copy()).isEmpty()) count++;
    return count;
  }

  /** Never group data-bearing stacks: their mod-defined semantics may differ. */
  public static boolean sameForSale(final ItemStack selected, final ItemStack candidate) {
    return !candidate.isEmpty() && selected.getItem() == candidate.getItem()
      && selected.getCurrentDurability() == candidate.getCurrentDurability()
      && (selected == candidate || selected.getExtraData() == null && candidate.getExtraData() == null);
  }

  public static int saleCount(final Inventory inventory, final ItemStack selected) {
    long count = 0;
    for(final ItemStack stack : inventory) {
      if(sameForSale(selected, stack)) count += stack.getSize();
    }
    return (int)Math.min(count, MAX_QUANTITY);
  }

  /** Ordinary per-unit hooks; a rejected unit stops the purchase, and is never charged. */
  public static int purchase(final int requested, final int price, final IntSupplier gold,
                             final Runnable beforeGive, final BooleanSupplier give, final IntConsumer charge) {
    int completed = 0;
    final int count = Math.clamp(requested, 0, MAX_QUANTITY);
    while(completed < count && affordable(gold.getAsInt(), price, 1) == 1) {
      beforeGive.run();
      if(affordable(gold.getAsInt(), price, 1) != 1 || !give.getAsBoolean()) break;
      charge.accept(price);
      completed++;
    }
    return completed;
  }
}
