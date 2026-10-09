// Definitive campaign presets (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.presets;

import java.util.*;

/** Auditable data, independent of engine initialization. No global/device settings or reward modifiers. */
public record PresetDefinition(String name, Map<String, Object> values) {
  public PresetDefinition { values = Collections.unmodifiableMap(new LinkedHashMap<>(values)); }
  public static PresetDefinition faithful() {
    final Map<String, Object> values = new LinkedHashMap<>();
    values.put("lod_core:addition_mode", "NORMAL");
    values.put("lod_core:addition_timing_window", 1.0f);
    values.put("lod_core:auto_dragoon_addition", false);
    values.put("lod_core:mash_mode", "MASH");
    values.put("lod_core:inventory_size", 32);
    values.put("lod_core:save_anywhere", false);
    values.put("lod_core:auto_save_after_battle", false);
    values.put("lod_core:disable_status_effects", false);
    values.put("lod_core:enemy_hp_bars", false);
    values.put("lod_core:secondary_character_xp_multiplier", 0.5f);
    values.put("lod_core:battle_transition_mode", "NORMAL");
    values.put("lod_core:transformation_mode", "NORMAL");
    values.put("lod_core:quick_text", "HOLD");
    values.put("lod_core:auto_text", false);
    values.put("lod_core:auto_text_delay", 1.0f);
    values.put("lod_core:unlock_party", false);
    values.put("lod_core:run_by_default", false);
    values.put("lod_core:encounter_rate", "RETAIL");
    values.put("lod_core:element_icon_config", false);
    values.put("lod_core:equip_effects_in_dragoon", false);
    values.put("lod_core:icon_set", "RETAIL");
    values.put("lod_core:item_group_sort_mode", "RETAIL");
    values.put("turn_order:show_turn_order", false);
    values.put("lod:extended_dragoon_actions", false);
    values.put("lod:item_stack_size", 1);
    values.put("lod:max_level", 60);
    values.put("lod:max_dragoon_level", 5);
    return new PresetDefinition("Faithful", values);
  }
  public static PresetDefinition definitive() {
    final Map<String, Object> values = new LinkedHashMap<>(faithful().values());
    values.put("lod_core:save_anywhere", true);
    values.put("lod_core:auto_save_after_battle", true);
    values.put("lod_core:run_by_default", true);
    values.put("lod_core:quick_text", "ALWAYS");
    values.put("lod_core:battle_transition_mode", "FAST");
    values.put("lod_core:transformation_mode", "SHORT");
    values.put("lod_core:enemy_hp_bars", true);
    values.put("lod_core:element_icon_config", true);
    values.put("turn_order:show_turn_order", true);
    values.put("lod_core:icon_set", "ENHANCED");
    values.put("lod_core:item_group_sort_mode", "ALPHABETICAL");
    return new PresetDefinition("Definitive", values);
  }
}
