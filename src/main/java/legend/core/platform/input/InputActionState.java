package legend.core.platform.input;

public class InputActionState {
  private static final long REPEAT_DELAY = 500_000_000L;
  private static final long REPEAT_INTERVAL = 100_000_000L;

  private State state = State.RELEASED;
  private float axis;
  private long timestamp;
  private boolean pendingPress;
  private boolean pendingRepeat;

  public void press() {
    this.pendingPress = true;
    this.pendingRepeat = false;
    this.state = State.JUST_PRESSED;
    this.axis = 0.0f;
    this.timestamp = System.nanoTime();
  }

  public void release() {
    // A short press/release still delivers its initial edge; release cancels held-key repeats.
    this.pendingRepeat = false;
    this.axis = 0.0f;
    this.state = State.RELEASED;
  }

  /** Cancellation is distinct from a normal button-up: discard unconsumed taps too. */
  public void cancel() { this.release(); this.consumeTick(); }

  public void axis(final float axis) {
    this.axis = axis;
  }

  public boolean repeat() {
    // Repeat is called after the event loop so this stops it from immediately going to HELD
    if(this.state == State.JUST_PRESSED) {
      this.state = State.PRESSED;
      return false;
    }

    final long time = System.nanoTime();

    if(this.state == State.PRESSED) {
      this.state = State.DELAY;
      this.timestamp = time;
      return false;
    }

    if(this.state == State.DELAY && time - this.timestamp >= REPEAT_DELAY) {
      this.state = State.REPEAT;
      this.pendingRepeat = true;
      this.timestamp = time;
      return true;
    }

    if(this.state == State.REPEAT && time - this.timestamp >= REPEAT_INTERVAL / 2) {
      this.state = State.HELD;
      this.timestamp = time;
      return false;
    }

    if(this.state == State.HELD && time - this.timestamp >= REPEAT_INTERVAL / 2) {
      this.state = State.REPEAT;
      this.pendingRepeat = true;
      this.timestamp = time;
      return true;
    }

    return false;
  }

  public boolean isPressed() {
    return this.pendingPress;
  }

  public boolean isRepeat() {
    return this.pendingPress || this.pendingRepeat;
  }

  public void consumeTick() { this.pendingPress = this.pendingRepeat = false; }

  public boolean isHeld() {
    return this.state != State.RELEASED;
  }

  public float getAxis() {
    return this.axis;
  }

  public enum State {
    RELEASED,
    JUST_PRESSED,
    PRESSED,
    DELAY,
    REPEAT,
    HELD,
  }
}
