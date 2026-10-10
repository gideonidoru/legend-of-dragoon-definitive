package legend.game.textures;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Private in-memory native sources permit mod changes without rereading game archives. */
public final class NativeUiSources {
  public static final NativeUiSources CURRENT = new NativeUiSources();
  private static final int MAX_FAMILIES = 8;
  private static final long MAX_BYTES = 2L * 1024 * 1024;
  private final Map<String, NativeUiTextureEvent> sources = new LinkedHashMap<>();
  private long bytes;

  public synchronized void remember(final NativeUiTextureEvent event) {
    if(event.encodedBytes() > MAX_BYTES) return;
    final NativeUiTextureEvent previous = this.sources.remove(event.id);
    if(previous != null) this.bytes -= previous.encodedBytes();
    while(!this.sources.isEmpty() && (this.sources.size() >= MAX_FAMILIES || this.bytes + event.encodedBytes() > MAX_BYTES)) {
      final String oldest = this.sources.keySet().iterator().next();
      this.bytes -= this.sources.remove(oldest).encodedBytes();
    }
    this.sources.put(event.id, event);
    this.bytes += event.encodedBytes();
  }

  public void replay(final Consumer<NativeUiTextureEvent> listener) {
    final List<NativeUiTextureEvent> snapshot;
    synchronized(this) { snapshot = List.copyOf(this.sources.values()); }
    // Listeners may acquire other locks or remember newer sources; never hold
    // the source-cache monitor while invoking the active mod event bus.
    snapshot.forEach(listener);
  }

  public synchronized long retainedBytes() { return this.bytes; }
}
