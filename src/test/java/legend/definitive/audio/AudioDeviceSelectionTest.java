package legend.definitive.audio;

import legend.core.audio.AudioDeviceSelection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AudioDeviceSelectionTest {
  private static final class Devices implements ToLongFunction<String> {
    private final List<String> attempts = new ArrayList<>();
    private final Map<String, Long> handles = new HashMap<>();

    Devices working(final String name, final long handle) {
      this.handles.put(name, handle);
      return this;
    }

    @Override
    public long applyAsLong(final String name) {
      this.attempts.add(name);
      return this.handles.getOrDefault(name, 0L);
    }
  }

  @Test void defaultUsesNativeSystemRouteEvenWhenHdmiIsListedFirst() {
    final Devices backend = new Devices().working("HDMI", 1).working(null, 2);
    assertEquals(2, AudioDeviceSelection.open("", List.of("HDMI", "Deck speakers"), backend));
    assertEquals(Arrays.asList((String)null), backend.attempts);
  }

  @Test void validSelectedDeviceIsHonoured() {
    final Devices backend = new Devices().working("USB", 3).working(null, 2);
    assertEquals(3, AudioDeviceSelection.open("USB", List.of("HDMI", "USB"), backend));
    assertEquals(List.of("USB"), backend.attempts);
  }

  @Test void failedSelectedDeviceFallsBackToNativeDefault() {
    final Devices backend = new Devices().working(null, 2);
    assertEquals(2, AudioDeviceSelection.open("USB", List.of("USB", "Deck speakers"), backend));
    assertEquals(Arrays.asList("USB", null), backend.attempts);
  }

  @Test void staleSelectionFallsBackWithoutRewritingPreference() {
    final Devices backend = new Devices().working(null, 2);
    assertEquals(2, AudioDeviceSelection.open("unplugged USB", List.of("HDMI"), backend));
    assertEquals(Arrays.asList((String)null), backend.attempts);
  }

  @Test void missingEnumerationStillAttemptsNativeDefault() {
    for(final List<String> listed : Arrays.asList(null, List.<String>of())) {
      final Devices backend = new Devices().working(null, 2);
      assertEquals(2, AudioDeviceSelection.open("", listed, backend));
      assertEquals(Arrays.asList((String)null), backend.attempts);
    }
  }

  @Test void failedDefaultCanRecoverThroughAnotherListedDevice() {
    final Devices backend = new Devices().working("Deck speakers", 2);
    assertEquals(2, AudioDeviceSelection.open("USB", List.of("USB", "HDMI", "Deck speakers"), backend));
    assertEquals(Arrays.asList("USB", null, "HDMI", "Deck speakers"), backend.attempts);
  }

  @Test void totalFailureTriesEveryDistinctUsableNameAtMostOnce() {
    final Devices backend = new Devices();
    assertEquals(0, AudioDeviceSelection.open("USB", Arrays.asList("USB", null, "", "HDMI", "USB", "HDMI"), backend));
    assertEquals(Arrays.asList("USB", null, "HDMI"), backend.attempts);
  }
}
