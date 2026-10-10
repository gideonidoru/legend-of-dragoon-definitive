package legend.definitive.fmv;

import legend.game.fmv.RumbleData;
import legend.game.unpacker.FileData;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.jupiter.api.Assertions.*;

final class RumbleTimelineTest {
  private static RumbleData cue(final int frame, final int initial, final int ending, final int duration) {
    final var bytes = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
    bytes.putInt(frame).putShort((short)initial).putShort((short)ending).putInt(duration);
    return new RumbleData(new FileData(bytes.array()));
  }

  @Test void skippedFramesDoNotSubtractElapsedTwice() {
    final var timeline = new RumbleTimeline(new RumbleData[]{cue(105, 100, 200, 20)});
    final var update = timeline.advance(100, 110);
    assertEquals(15, update.remainingFrames());
    assertEquals(125, update.initial());
    assertFalse(update.stop());
    assertFalse(timeline.advance(111, 124).stop());
    assertTrue(timeline.advance(125, 125).stop());
  }

  @Test void expiredCrossedCueIsStoppedInsteadOfStarted() {
    final var timeline = new RumbleTimeline(new RumbleData[]{cue(105, 100, 200, 3)});
    final var update = timeline.advance(100, 110);
    assertEquals(-1, update.initial());
    assertTrue(update.stop());
  }

  @Test void lastCrossedCueOwnsOutputAndZeroDurationStops() {
    final var timeline = new RumbleTimeline(new RumbleData[]{cue(5, 100, 200, 20), cue(7, 150, 200, 0)});
    assertTrue(timeline.advance(0, 8).stop());
  }
}
