package legend.definitive.qol;

import legend.game.modding.coremod.config.RewardMultiplierConfigEntry;
import legend.game.saves.ConfigStorageLocation;
import legend.definitive.presets.PresetDefinition;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class RewardScalingTest {
  @Test void independentMultipliersAndPerEnemyRounding() {
    final RewardScaling settings = new RewardScaling(0.5f, 2.0f);
    assertEquals(1, settings.xp(1));
    assertEquals(3, settings.xp(1) + settings.xp(1) + settings.xp(1));
    assertEquals(22, settings.gold(11));
    assertEquals(0, new RewardScaling(0, 0).xp(99));
    assertEquals(25, new RewardScaling(0.25f, 1).xp(100));
    assertEquals(1000, new RewardScaling(10, 1).xp(100));
    assertEquals(100, new RewardScaling(1, 1).xp(100));
  }

  @Test void corruptedConfigurationFallsBackToNormal() {
    final RewardMultiplierConfigEntry entry = new RewardMultiplierConfigEntry();
    assertEquals(ConfigStorageLocation.CAMPAIGN, entry.storageLocation);
    assertFalse(entry.availableInBattle());
    for(final float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -1, 10.25f, 0.3f}) {
      final byte[] bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(invalid).array();
      assertEquals(1.0f, entry.deserializer.apply(bytes));
      assertEquals(1.0f, entry.deserializer.apply(entry.serializer.apply(invalid)));
    }
    assertEquals(1.0f, entry.deserializer.apply(new byte[]{0}));
    for(int quarter = 0; quarter <= 40; quarter++) {
      final float value = quarter / 4.0f;
      assertEquals(value, entry.deserializer.apply(entry.serializer.apply(value)));
    }
  }

  @Test void rewardAndAccumulationBoundsDoNotWrap() {
    assertEquals(0, new RewardScaling(10, 10).xp(-1));
    assertEquals(RewardScaling.MAX_REWARD, new RewardScaling(10, 10).gold(Integer.MAX_VALUE));
    assertEquals(RewardScaling.MAX_REWARD, RewardScaling.accumulate(RewardScaling.MAX_REWARD, Integer.MAX_VALUE));
  }

  @Test void bothPresetsExplicitlyKeepNormalRewardsAndManualFeedbackOff() {
    for(final var preset : new PresetDefinition[]{PresetDefinition.faithful(), PresetDefinition.definitive()}) {
      assertEquals(1.0f, preset.values().get("lod_core:enemy_xp_multiplier"));
      assertEquals(1.0f, preset.values().get("lod_core:enemy_gold_multiplier"));
      assertEquals(false, preset.values().get("lod_core:addition_feedback"));
      assertEquals(1.0f, preset.values().get("lod_core:addition_timing_window"));
    }
  }
}
