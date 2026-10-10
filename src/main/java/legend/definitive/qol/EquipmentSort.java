package legend.definitive.qol;

import legend.game.inventory.Equipment;
import legend.game.types.EquipmentSlot;

import java.util.Comparator;
import java.util.function.Function;

public enum EquipmentSort {
  SLOT, NAME, POWER;

  public EquipmentSort next() {
    return values()[(this.ordinal() + 1) % values().length];
  }

  /** Power is attack for weapons, defence for armour, speed for boots/accessories. */
  public Comparator<Equipment> comparator(final Function<Equipment, String> name) {
    final Comparator<Equipment> alphabetic = Comparator.comparing(name, String.CASE_INSENSITIVE_ORDER);
    return switch(this) {
      case NAME -> alphabetic;
      case SLOT -> Comparator.comparing((Equipment e) -> e.slot).thenComparing(alphabetic);
      case POWER -> Comparator.comparing((Equipment e) -> e.slot)
        .thenComparing(Comparator.comparingInt(EquipmentSort::power).reversed()).thenComparing(alphabetic);
    };
  }

  public static int power(final Equipment equipment) {
    return switch(equipment.slot) {
      case WEAPON -> equipment.attack_10;
      case HELMET, ARMOUR -> equipment.defence_12;
      case BOOTS, ACCESSORY -> equipment.speed_0f;
    };
  }

  public static boolean matches(final EquipmentSlot filter, final Equipment equipment) {
    return filter == null || filter == equipment.slot;
  }
}
