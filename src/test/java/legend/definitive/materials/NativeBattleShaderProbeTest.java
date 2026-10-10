package legend.definitive.materials;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static legend.definitive.materials.NativeBattleShaderProbe.*;

/** Original fixtures for the readback oracle; these tests create no GPU context. */
class NativeBattleShaderProbeTest {
  @Test void transparencyBitDoesNotMakeBlackInvisible() {
    assertFalse(visible(0, Sample.OPAQUE_UNLIT));
    assertTrue(visible(0x8000, Sample.OPAQUE_UNLIT));
    assertFalse(visible(0x8000, Sample.TRANSLUCENT_OPAQUE_PASS));
    assertTrue(visible(0x8000, Sample.TRANSLUCENT_STP_PASS));
    assertFalse(visible(31, Sample.TRANSLUCENT_STP_PASS));
    assertTrue(visible(31, Sample.TRANSLUCENT_OPAQUE_PASS));
  }
  @Test void oracleHasKnownCoverageAndBlackCountsForAllPasses() {
    final int[] counts = {6144,6144,6144,2048,4096,0,8192,8192};
    final int[] black = {2048,2048,2048,0,2048,0,0,0};
    for(final Sample sample : Sample.values()) {
      final byte[] pixels = expected(sample); final Difference result = compare(pixels, pixels);
      assertEquals(counts[sample.ordinal()], result.visiblePixels(), sample.label());
      assertEquals(black[sample.ordinal()], result.visibleBlackPixels(), sample.label());
      assertEquals(0, result.coverageMismatches()); assertEquals(0, result.maximumChannelError());
    }
  }
  @Test void oracleChecksLightingPaletteAndPassAlpha() {
    final byte[] unlit = expected(Sample.OPAQUE_UNLIT), lit = expected(Sample.UNTEXTURED_LIT), translucent = expected(Sample.TRANSLUCENT_COMBINED);
    assertEquals(0, Byte.toUnsignedInt(unlit[3]));
    assertEquals(255, Byte.toUnsignedInt(unlit[16 * 4 + 3]));
    assertEquals(255, Byte.toUnsignedInt(unlit[32 * 4]));
    assertEquals(255, Byte.toUnsignedInt(unlit[(64 + 32) * 4 + 1]));
    assertEquals(217, Byte.toUnsignedInt(lit[0])); assertEquals(204, Byte.toUnsignedInt(lit[1])); assertEquals(191, Byte.toUnsignedInt(lit[2]));
    assertEquals(128, Byte.toUnsignedInt(translucent[32 * 4 + 3]));
  }
  @Test void liveFxOracleChangesColoursAndSubtexelsWithoutChangingCoverageOrBlack() {
    for(final Sample sample : Sample.values()) {
      final byte[] nativePixels = expected(sample), fx = expectedFx(sample);
      final Difference result = compare(nativePixels, fx);
      assertEquals(0, result.coverageMismatches());
      assertEquals(compare(nativePixels, nativePixels).visibleBlackPixels(), result.visibleBlackPixels());
      if((sample.flags & 2) != 0 && result.visiblePixels() > 0) assertTrue(result.maximumChannelError() > 0);
    }
    final byte[] fx = expectedFx(Sample.OPAQUE_UNLIT);
    assertNotEquals(fx[32 * 4], fx[40 * 4]);
    assertNotEquals(fx[32 * 4], fx[96 * 4]);
  }
  @Test void comparisonDetectsLostBlackWrongColourAndMalformedReadback() {
    final byte[] control = expected(Sample.OPAQUE_UNLIT), changed = control.clone();
    changed[16 * 4 + 3] = 0;
    assertEquals(1, compare(control, changed).coverageMismatches());
    assertEquals(2047, compare(control, changed).visibleBlackPixels());
    final byte[] wrongColour = control.clone(); wrongColour[32 * 4] = (byte)245;
    assertEquals(10, compare(control, wrongColour).maximumChannelError()); assertEquals(0, compare(control, wrongColour).coverageMismatches());
    assertThrows(IllegalArgumentException.class, () -> compare(control, new byte[4]));
  }
}
