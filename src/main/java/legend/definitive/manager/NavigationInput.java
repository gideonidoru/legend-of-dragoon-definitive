// Definitive input arbitration (2026-10-10), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.awt.event.KeyEvent;
import java.util.*;

/** One accepted action across Steam keyboard emulation, SDL buttons and stick events. */
final class NavigationInput {
  enum Source { KEYBOARD, BUTTON, AXIS }
  private record DeviceSource(Source source, int device) { }
  private final Map<DeviceSource, Set<Integer>> held = new HashMap<>();
  private final Map<Integer, Long> last = new HashMap<>(), repeat = new HashMap<>();
  synchronized boolean press(final Source source, final int key, final long now) { return this.press(source, 0, key, now); }
  synchronized boolean press(final Source source, final int device, final int key, final long now) {
    if(key == 0) return false;
    final boolean initial = this.held.computeIfAbsent(new DeviceSource(source, device), unused -> new HashSet<>()).add(key);
    final long previous = this.last.getOrDefault(key, Long.MIN_VALUE / 2);
    if(now - previous < 160_000_000L) return false;
    if(!initial && (!directional(key) || now < this.repeat.getOrDefault(key, Long.MAX_VALUE))) return false;
    this.last.put(key, now);
    this.repeat.put(key, now + (initial ? 450_000_000L : 180_000_000L));
    return true;
  }
  synchronized void release(final Source source, final int key) { this.release(source, 0, key); }
  synchronized void release(final Source source, final int device, final int key) { final Set<Integer> keys = this.held.get(new DeviceSource(source, device)); if(keys != null) keys.remove(key); }
  synchronized void releaseDevice(final int device) { this.held.keySet().removeIf(input -> input.source() != Source.KEYBOARD && input.device() == device); }
  synchronized void clear() { this.held.clear(); this.last.clear(); this.repeat.clear(); }
  static boolean directional(final int key) { return key == KeyEvent.VK_UP || key == KeyEvent.VK_DOWN || key == KeyEvent.VK_LEFT || key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_PAGE_UP || key == KeyEvent.VK_PAGE_DOWN; }
}
