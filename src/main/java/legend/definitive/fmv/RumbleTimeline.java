package legend.definitive.fmv;

import legend.game.fmv.RumbleData;

/** Rumble remains on the original 15 fps media timeline, including skipped display frames. */
public final class RumbleTimeline {
  public record Update(int initial, int ending, int remainingFrames, boolean stop) { }
  private final RumbleData[] cues;
  private int endFrame = -1;

  public RumbleTimeline(final RumbleData[] cues) { this.cues = cues == null ? new RumbleData[0] : cues; }

  public Update advance(final int firstFrame, final int targetFrame) {
    RumbleData latest = null;
    for(final RumbleData cue : this.cues) {
      if(cue.frame >= firstFrame && cue.frame <= targetFrame && (latest == null || cue.frame >= latest.frame)) latest = cue;
    }
    if(latest != null) {
      final long end = (long)latest.frame + latest.duration;
      if(latest.duration <= 0 || targetFrame >= end || end > Integer.MAX_VALUE) {
        this.endFrame = -1;
        return new Update(-1, 0, 0, true);
      }
      this.endFrame = (int)end;
      final int elapsed = targetFrame - latest.frame;
      final int intensity = latest.initialIntensity + (int)((long)(latest.endingIntensity - latest.initialIntensity) * elapsed / latest.duration);
      return new Update(intensity, latest.endingIntensity, this.endFrame - targetFrame, false);
    }
    if(this.endFrame >= 0 && targetFrame >= this.endFrame) {
      this.endFrame = -1;
      return new Update(-1, 0, 0, true);
    }
    return new Update(-1, 0, 0, false);
  }
}
