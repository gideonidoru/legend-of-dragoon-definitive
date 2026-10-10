package legend.core.audio;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.function.ToLongFunction;

/** Opens an audio output through the native device boundary. A null name requests the OS default. */
public final class AudioDeviceSelection {
  private AudioDeviceSelection() { }

  public static long open(final String selected, final List<String> devices, final ToLongFunction<String> opener) {
    final var remaining = new LinkedHashSet<String>();
    if(devices != null) {
      for(final String device : devices) {
        if(device != null && !device.isEmpty()) {
          remaining.add(device);
        }
      }
    }

    if(remaining.remove(selected)) {
      final long device = opener.applyAsLong(selected);
      if(device != 0) {
        return device;
      }
    }

    final long systemDefault = opener.applyAsLong(null);
    if(systemDefault != 0) {
      return systemDefault;
    }

    for(final String name : remaining) {
      final long device = opener.applyAsLong(name);
      if(device != 0) {
        return device;
      }
    }

    return 0;
  }
}
