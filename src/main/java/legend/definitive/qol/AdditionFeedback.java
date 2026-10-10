package legend.definitive.qol;

/** Presentation only: observes resolved hits, never changes their result or timing. */
public final class AdditionFeedback {
  public enum Result { EARLY, LATE, WRONG_BUTTON, SUCCESS, PERFECT }

  private Result result;
  private int remainingTicks;

  public void record(final int completion, final int frame, final int lower, final int upper, final boolean automatic) {
    if(automatic || completion == 0) return;
    this.result = switch(completion) {
      case -1 -> Result.EARLY;
      case -2 -> Result.LATE;
      case -3 -> Result.WRONG_BUTTON;
      case 1 -> frame == lower + (upper - lower) / 2 ? Result.PERFECT : Result.SUCCESS;
      default -> null;
    };
    this.remainingTicks = this.result != null ? 24 : 0;
  }

  public void clear() {
    this.result = null;
    this.remainingTicks = 0;
  }

  public void tick() {
    if(this.remainingTicks > 0) this.remainingTicks--;
  }

  public Result visibleResult() {
    return this.remainingTicks > 0 ? this.result : null;
  }
}
