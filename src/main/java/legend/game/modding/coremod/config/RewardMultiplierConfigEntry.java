package legend.game.modding.coremod.config;

import legend.core.MathHelper;
import legend.definitive.qol.RewardScaling;
import legend.game.inventory.screens.controls.NumberSpinner;
import legend.game.saves.ConfigCategory;
import legend.game.saves.ConfigEntry;
import legend.game.saves.ConfigStorageLocation;

public class RewardMultiplierConfigEntry extends ConfigEntry<Float> {
  public RewardMultiplierConfigEntry() {
    super(1.0f, ConfigStorageLocation.CAMPAIGN, ConfigCategory.GAMEPLAY,
      RewardMultiplierConfigEntry::encode, RewardMultiplierConfigEntry::decode);
    this.setEditControl((current, config) -> {
      final NumberSpinner<Float> spinner = NumberSpinner.floatSpinner(RewardScaling.validate(current), 0.25f, 1.0f, 0.0f, 10.0f);
      spinner.onChange(value -> config.setConfig(this, RewardScaling.validate(value)));
      return spinner;
    });
  }

  private static byte[] encode(final float value) {
    final byte[] bytes = new byte[4];
    MathHelper.setInt(bytes, 0, Float.floatToIntBits(RewardScaling.validate(value)));
    return bytes;
  }

  private static float decode(final byte[] bytes) {
    return bytes.length == 4 ? RewardScaling.validate(Float.intBitsToFloat(MathHelper.getInt(bytes, 0))) : 1.0f;
  }

  @Override public boolean hasHelp() { return true; }
  @Override public boolean availableInBattle() { return false; }
}
