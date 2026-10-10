package legend.definitive.models;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Original synthetic motion only; tests call the real source interpolation methods. */
class NativePoseProbeTest {
  @TempDir Path temporary;
  static byte[] fixture() {
    final var data = ByteBuffer.allocate(16 + 2 * 2 * 12).order(ByteOrder.LITTLE_ENDIAN);
    data.putInt(0, 12); data.putShort(12, (short)2); data.putShort(14, (short)4);
    data.position(16);
    for(final int[] transform : new int[][]{{0,0,0,10,20,30},{1024,0,0,-10,5,15},{0,0,1024,30,40,50},{1024,0,0,-30,15,25}}) for(final int value : transform) data.putShort((short)value);
    return data.array();
  }
  @Test void combatSourceInterpolationHasTheExpectedHalfPoseAndPartOrder() {
    final var profiles = NativePoseProbe.inspect(fixture());
    assertEquals(3, profiles.size());
    final float[] half = profiles.getFirst().samples().get(2).matrices();
    assertEquals(20, half[3]); assertEquals(30, half[7]); assertEquals(40, half[11]);
    assertEquals(Math.sqrt(.5), half[0], 2e-6); assertEquals(-Math.sqrt(.5), half[1], 2e-6);
    assertEquals(-20, half[15]); assertEquals(10, half[19]); assertEquals(20, half[23]);
    assertEquals(0, half[17], 2e-6); assertEquals(-1, half[18], 2e-6);
  }
  @Test void progressiveFourFrameQuaternionInterpolationIsNotAssumedLinear() {
    final var profiles = NativePoseProbe.inspect(fixture());
    final float[] middle = profiles.get(1).samples().get(5).matrices();
    assertEquals(20, middle[3]); assertEquals(30, middle[7]);
    assertTrue(middle[0] < .65 && middle[0] > .5, "Progressive nlerp differs from a direct 45-degree halfway rotation");
    assertTrue(Math.abs(middle[0] - Math.sqrt(.5)) > .05);
  }
  @Test void directAnimationApplyAndCombatRoutesKeepTheirDistinctKeyframeConventions() {
    final var profiles = NativePoseProbe.inspect(fixture());
    final float[] combat = profiles.getFirst().samples().get(1).matrices();
    final float[] apply = profiles.get(2).samples().get(1).matrices();
    assertEquals(10, combat[3]); assertEquals(20, apply[3]);
    assertEquals(1, combat[0], 2e-6); assertEquals(Math.sqrt(.5), apply[0], 2e-6);
  }
  @Test void sourceBytesAndEarlierSnapshotsRemainImmutable() {
    final byte[] input = fixture(), original = input.clone();
    final var profiles = NativePoseProbe.inspect(input);
    assertArrayEquals(original, input);
    final float[] first = profiles.getFirst().samples().getFirst().matrices(); first[3] = 999;
    assertEquals(10, profiles.getFirst().samples().getFirst().matrices()[3]);
    assertThrows(UnsupportedOperationException.class, () -> profiles.clear());
  }
  @Test void malformedOrExcessiveInputIsRejectedBeforeAnimationConstruction() {
    assertThrows(IllegalArgumentException.class, () -> NativePoseProbe.inspect(new byte[15]));
    final byte[] data = fixture(); data[0] = 1; assertThrows(IllegalArgumentException.class, () -> NativePoseProbe.inspect(data));
    final byte[] odd = fixture(); odd[14] = 3; assertThrows(IllegalArgumentException.class, () -> NativePoseProbe.inspect(odd));
    final byte[] count = fixture(); count[12] = 0; assertThrows(IllegalArgumentException.class, () -> NativePoseProbe.inspect(count));
    final byte[] tooLarge = new byte[16 + 256 * 256 * 12];
    final var header = ByteBuffer.wrap(tooLarge).order(ByteOrder.LITTLE_ENDIAN); header.putInt(0,12);header.putShort(12,(short)256);header.putShort(14,(short)512);
    assertThrows(IllegalArgumentException.class, () -> NativePoseProbe.inspect(tooLarge));
  }
  @Test void reportPublicationKeepsExistingFilesAndRejectsCheckoutPaths() throws Exception {
    final Path input = this.temporary.resolve("motion"); Files.write(input, fixture());
    final Path output = this.temporary.resolve("poses.json");
    NativePoseProbe.main(new String[]{input.toString(),output.toString()});
    final byte[] original = Files.readAllBytes(output);
    assertTrue(Files.readString(output).contains("row-major 3x4"));
    assertThrows(FileAlreadyExistsException.class, () -> NativePoseProbe.main(new String[]{input.toString(),output.toString()}));
    assertArrayEquals(original, Files.readAllBytes(output));
    final Path link = this.temporary.resolve("existing-report-link"); Files.createSymbolicLink(link, output);
    assertThrows(FileAlreadyExistsException.class, () -> NativePoseProbe.main(new String[]{input.toString(),link.toString()}));
    assertArrayEquals(original, Files.readAllBytes(output));
    assertThrows(java.io.IOException.class, () -> NativePoseProbe.main(new String[]{input.toString(),output.getRoot().toString()}));
    final Path forbidden = Path.of("build/reports/private-motion-must-not-be-created.json").toAbsolutePath();
    Files.createDirectories(forbidden.getParent());
    assertThrows(java.io.IOException.class, () -> NativePoseProbe.main(new String[]{input.toString(),forbidden.toString()}));
    assertFalse(Files.exists(forbidden));
    try(final var files = Files.list(this.temporary)) { assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith(".definitive-private-poses-"))); }
  }
}
