// Definitive preset acceptance (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.presets;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class PresetDefinitionTest {
  @Test void convenienceChoicesPreserveTimingRewardsEncountersAndCapacity() {
    final var faithful = PresetDefinition.faithful().values(); final var definitive = PresetDefinition.definitive().values();
    for(final String id : Set.of("lod_core:addition_mode", "lod_core:addition_timing_window", "lod_core:auto_dragoon_addition", "lod_core:mash_mode", "lod_core:inventory_size", "lod_core:encounter_rate", "lod_core:secondary_character_xp_multiplier", "lod:item_stack_size", "lod:max_level", "lod:max_dragoon_level")) assertEquals(faithful.get(id), definitive.get(id), id);
    assertEquals(false, faithful.get("lod_core:save_anywhere")); assertEquals(true, definitive.get("lod_core:save_anywhere"));
    assertEquals(false, faithful.get("lod_core:auto_save_after_battle")); assertEquals(true, definitive.get("lod_core:auto_save_after_battle"));
    assertThrows(UnsupportedOperationException.class, () -> faithful.put("lod_core:inventory_size", 999));
  }
  @Test void definitionsAreIndependentAndDoNotContainDeviceOrPresentationPackSettings() {
    assertNotSame(PresetDefinition.faithful().values(), PresetDefinition.faithful().values());
    assertTrue(PresetDefinition.faithful().values().keySet().stream().noneMatch(k -> k.contains("volume") || k.contains("keybind") || k.contains("scbackgroundhd") || k.contains("resolution") || k.contains("upscal")));
  }
}
