package legend.game.dabas;

import legend.core.audio.GenericSource;
import org.lwjgl.BufferUtils;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import static org.legendofdragoon.dabas.core.sound.Spu.SAMPLES_PER_TICK;

/** Ordered handoff from the hardware timer producer; rendering never waits for audio space. */
final class DabasAudio {
  static final int MAX_PENDING = 4;
  private final GenericSource source;
  private final ByteBuffer upload = BufferUtils.createByteBuffer(SAMPLES_PER_TICK);
  private final ArrayDeque<byte[]> pending = new ArrayDeque<>();
  private int generation;
  private boolean closed;
  private boolean paused;

  DabasAudio(final GenericSource source) {
    this.source = source;
    this.generation = source.generation();
  }

  synchronized void submit(final byte[] samples) {
    if(samples.length != SAMPLES_PER_TICK) throw new IllegalArgumentException("Dabas PCM block has the wrong length");
    final int generation = this.source.generation();
    this.drain();
    // Hardware emits these 100 ms blocks on its timer thread, independently of draw/catch-up.
    // Backpressure that producer rather than losing PCM or blocking the renderer.
    while(!this.closed && this.source.outputAvailable() && generation == this.source.generation() && (this.paused || this.pending.size() == MAX_PENDING)) {
      try { this.wait(10); }
      catch(final InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
      this.drain();
    }
    if(this.closed || !this.source.outputAvailable() || generation != this.source.generation()) return;
    this.pending.addLast(samples.clone()); // SPU immediately reuses its array.
    this.drain();
  }

  synchronized void drain() {
    synchronized(this.source) {
      if(this.source.generation() != this.generation || !this.source.outputAvailable()) {
        this.pending.clear();
        this.generation = this.source.generation();
      }
      while(!this.closed && !this.paused && !this.pending.isEmpty() && this.source.availableBuffers() > 1) {
        this.upload.clear();
        this.upload.put(this.pending.removeFirst()).flip();
        this.source.bufferOutput(this.upload);
      }
    }
    this.notifyAll();
  }

  synchronized void setPaused(final boolean paused) { this.paused = paused; this.notifyAll(); }

  synchronized int pendingBlocks() { return this.pending.size(); }
  synchronized void close() { this.closed = true; this.pending.clear(); this.notifyAll(); }
}
