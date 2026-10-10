package legend.definitive.qol;

/** A battle's immutable settings, applied after reward events, before party distribution. */
public record RewardScaling(float xpMultiplier, float goldMultiplier) {
  public static final int MAX_REWARD = 99_999_999;

  public RewardScaling {
    xpMultiplier = validate(xpMultiplier);
    goldMultiplier = validate(goldMultiplier);
  }

  /** Reject corrupt/out-of-range/non-quarter-step values to the ordinary 1x default. */
  public static float validate(final float value) {
    return Float.isFinite(value) && value >= 0.0f && value <= 10.0f && value * 4 == Math.round(value * 4) ? value : 1.0f;
  }

  public int xp(final int base) {
    return scale(base, this.xpMultiplier);
  }

  public int gold(final int base) {
    return scale(base, this.goldMultiplier);
  }

  private static int scale(final int base, final float multiplier) {
    return (int)Math.clamp(Math.round(Math.max(0, base) * (double)multiplier), 0L, MAX_REWARD);
  }

  public static int accumulate(final int total, final int reward) {
    return (int)Math.clamp((long)total + reward, 0L, MAX_REWARD);
  }
}
