package legend.definitive.artwork;

import java.util.List;

/** Generation gate shared by native callbacks and optional source snapshots. */
public final class WorldTerrainLoad {
  public record Snapshot(byte[] model, List<byte[]> bank) {
    public Snapshot { model = model.clone(); bank = bank.stream().map(byte[]::clone).toList(); }
    @Override public byte[] model() { return this.model.clone(); }
    @Override public List<byte[]> bank() { return this.bank.stream().map(byte[]::clone).toList(); }
  }
  private long generation;
  private byte[] model;
  private List<byte[]> bank;
  public synchronized long begin() { this.model = null; this.bank = null; return ++this.generation; }
  public synchronized void invalidate() { this.begin(); }
  public synchronized void model(final long token, final byte[] source, final Runnable nativeLoad) {
    if(token != this.generation) return;
    // TMD parsing mutates packet headers, so clone before invoking the native load.
    final byte[] copy = source.length <= 1024 * 1024 ? source.clone() : null;
    nativeLoad.run(); this.model = copy;
  }
  public synchronized void bank(final long token, final List<byte[]> sources, final Runnable nativeLoad) {
    if(token != this.generation) return;
    final List<byte[]> copy = !sources.isEmpty() && sources.size() <= 256 && sources.stream().allMatch(b -> b.length <= 1024 * 1024) && sources.stream().mapToLong(b -> b.length).sum() <= 16 * 1024 * 1024 ? sources.stream().map(byte[]::clone).toList() : null;
    nativeLoad.run(); this.bank = copy;
  }
  public synchronized Snapshot ready() { return this.model == null || this.bank == null ? null : new Snapshot(this.model, this.bank); }
}
