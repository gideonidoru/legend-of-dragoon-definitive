package legend.core.font;

import it.unimi.dsi.fastutil.chars.Char2ObjectMaps;
import legend.core.gte.MV;
import legend.core.renderer.QueuedModelStandard;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Objects;
import java.util.function.Consumer;

/** Stable reference for controls; optional artwork never rewrites the configured font. */
public final class EffectiveFont extends Font {
  private static final Logger LOGGER = LogManager.getLogger(EffectiveFont.class);
  private final Consumer<Runnable> retirement;
  private Font configured;
  private Font owned;
  private long generation;
  private long glyphGeneration = Long.MIN_VALUE;

  public EffectiveFont(final Font configured, final Consumer<Runnable> retirement) {
    super(configured.path, configured.name, Char2ObjectMaps.emptyMap());
    this.configured = Objects.requireNonNull(configured);
    this.retirement = Objects.requireNonNull(retirement);
  }

  public synchronized Font configured() { return this.configured; }
  public synchronized void configure(final Font font) {
    Objects.requireNonNull(font);
    if(font instanceof EffectiveFont || font.isOwned()) throw new IllegalArgumentException("An owned or effective font cannot be configured");
    if(font != this.configured) { this.configured = font; this.generation++; }
  }

  /** Takes exclusive ownership. The previously owned selection retires after renderer queue reset. */
  public synchronized void selectOwned(final Font font) {
    if(font instanceof EffectiveFont || font == this.configured) throw new IllegalArgumentException("Shared configured font cannot be owned");
    if(font == this.owned) return;
    if(font != null) font.claimOwnership(this);
    final Font previous = this.owned;
    this.owned = font;
    this.generation++;
    if(previous != null) {
      previous.scheduleRetirement();
      if(previous.hasOwnedResources()) this.retirement.accept(previous::releaseOwnedResources);
      else previous.releaseOwnedResources();
    }
  }

  private Font effective() { return this.owned == null ? this.configured : this.owned; }
  @Override public synchronized long generation() {
    final long glyphGeneration = this.effective().generation();
    if(glyphGeneration != this.glyphGeneration) {
      this.glyphGeneration = glyphGeneration;
      this.generation++;
    }
    return this.generation;
  }
  @Override public synchronized void init() {
    if(this.owned != null) {
      try { this.owned.init(); return; }
      catch(final RuntimeException failure) {
        LOGGER.warn("Optional font initialization failed; restoring configured font", failure);
        this.selectOwned(null);
      }
    }
    this.configured.init();
  }
  @Override public synchronized int charWidth(final char chr) { return this.effective().charWidth(chr); }
  @Override public synchronized boolean usesColour(final char chr) { return this.effective().usesColour(chr); }
  @Override public synchronized QueuedModelStandard queueChar(final char chr, final MV transforms) { return this.effective().queueChar(chr, transforms); }
  @Override public void releaseOwnedResources() { throw new IllegalStateException("Effective font does not own configured resources"); }
}
