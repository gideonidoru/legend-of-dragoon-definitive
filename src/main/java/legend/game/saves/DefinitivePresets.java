// Definitive campaign preset adapter (2026-10-09), AGPL v3; see LICENSE.
package legend.game.saves;

import legend.core.lang.RawText;
import legend.definitive.presets.PresetDefinition;
import org.legendofdragoon.modloader.registries.RegistryId;
import static legend.core.GameEngine.REGISTRIES;

public final class DefinitivePresets {
  private DefinitivePresets() { }
  public static ConfigPreset create(final PresetDefinition definition) {
    final ConfigCollection config = new ConfigCollection(false);
    for(final var setting : definition.values().entrySet()) {
      final var delegate = REGISTRIES.config.getEntry(new RegistryId(setting.getKey()));
      if(!delegate.isValid()) throw new IllegalStateException("Preset setting is unavailable: " + setting.getKey());
      final ConfigEntry<?> entry = delegate.get();
      if(entry.storageLocation != ConfigStorageLocation.CAMPAIGN && entry.storageLocation != ConfigStorageLocation.SAVE) throw new IllegalStateException("Preset cannot change device/global settings: " + setting.getKey());
      apply(config, entry, setting.getValue());
    }
    return new ConfigPreset(new RawText(definition.name()), config);
  }
  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void apply(final ConfigCollection config, final ConfigEntry entry, final Object value) {
    final Object defaultValue = entry.getDefaultValue(); final Object typed;
    if(defaultValue instanceof Enum<?> enumeration && value instanceof String name) typed = Enum.valueOf(enumeration.getDeclaringClass(), name);
    else if(defaultValue.getClass().isInstance(value)) typed = value;
    else throw new IllegalArgumentException("Preset value type does not match " + entry.getRegistryId());
    config.setConfigQuietly(entry, typed);
  }
}
