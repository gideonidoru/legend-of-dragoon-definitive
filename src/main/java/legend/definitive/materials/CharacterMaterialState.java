package legend.definitive.materials;

import legend.game.types.Model124;

/** Pairs independently arriving sources; GPU installation is consumed by rendering. */
public final class CharacterMaterialState {
  public record Update(byte[] modelSource, byte[] timSource) {
    public boolean ready() { return this.modelSource != null && this.timSource != null; }
  }
  private int generation;
  private long revision, consumed = -1;
  private Model124 owner;
  private byte[] modelSource, timSource;

  public synchronized int generation() { return this.generation; }
  public synchronized void runIfCurrent(final int generation, final Runnable callback) {
    if(this.generation == generation) callback.run();
  }

  public synchronized void invalidate() {
    this.generation++;
    this.revision++;
    this.owner = null;
    this.modelSource = null;
    this.timSource = null;
  }

  public synchronized void modelReady(final Model124 owner, final byte[] source) {
    this.owner = owner;
    this.modelSource = source;
    this.revision++;
  }

  public synchronized void textureReady(final byte[] source) {
    this.timSource = source;
    this.revision++;
  }

  /** Null means no new pair; an incomplete update still clears any prior appearance. */
  public synchronized Update takeUpdate(final Model124 owner) {
    if(this.owner != owner || this.consumed == this.revision) return null;
    this.consumed = this.revision;
    return new Update(this.modelSource, this.timSource);
  }
}
