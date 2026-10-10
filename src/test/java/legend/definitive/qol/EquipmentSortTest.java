package legend.definitive.qol;

import legend.game.characters.ElementSet;
import legend.game.inventory.Equipment;
import legend.game.inventory.ItemIcon;
import legend.game.types.EquipmentSlot;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentSortTest {
  private static Equipment equipment(final EquipmentSlot slot, final int attack, final int defence, final int speed) {
    return new Equipment(0, 0, slot, null, new ElementSet(), new ElementSet(),
      0, 0, 0, 0, 0, 0, 0, 0, false, false, false, false, 0, 0, 0, 0, 0,
      ItemIcon.SWORD, speed, attack, 0, defence, 0, 0, 0, 0, 0, 0, 0);
  }

  @Test void powerSortUsesRelevantStatWithinEachSlotAndNamesBreakTies() {
    final Equipment weak = equipment(EquipmentSlot.WEAPON, 5, 100, 100);
    final Equipment strong = equipment(EquipmentSlot.WEAPON, 20, 0, 0);
    final Equipment armour = equipment(EquipmentSlot.ARMOUR, 999, 8, 0);
    final Map<Equipment, String> names = Map.of(weak, "A", strong, "Z", armour, "B");
    final List<Equipment> list = new ArrayList<>(List.of(armour, weak, strong));
    list.sort(EquipmentSort.POWER.comparator(names::get));
    assertEquals(List.of(strong, weak, armour), list);
    assertEquals(8, EquipmentSort.power(armour));
    list.sort(EquipmentSort.NAME.comparator(names::get));
    assertEquals(List.of(weak, armour, strong), list);
  }

  @Test void slotFiltersAndSortCycleDoNotDropOtherSlots() {
    final Equipment weapon = equipment(EquipmentSlot.WEAPON, 1, 0, 0);
    assertTrue(EquipmentSort.matches(null, weapon));
    assertTrue(EquipmentSort.matches(EquipmentSlot.WEAPON, weapon));
    assertFalse(EquipmentSort.matches(EquipmentSlot.BOOTS, weapon));
    assertEquals(EquipmentSort.SLOT, EquipmentSort.SLOT.next().next().next());
  }
}
