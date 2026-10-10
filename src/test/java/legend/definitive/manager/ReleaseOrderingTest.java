package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

/** GitHub can list the older designated release ahead of a newer compatible alpha. */
class ReleaseOrderingTest {
  private static String release(final String tag, final int id, final String date) {
    return "{\"draft\":false,\"tag_name\":\"" + tag + "\",\"published_at\":\"" + date + "\",\"assets\":[{\"id\":" + id + ",\"name\":\"Legend-of-Dragoon-Definitive-linux-x64.zip\",\"digest\":\"sha256:" + "a".repeat(64) + "\",\"browser_download_url\":\"https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/" + tag + "/package.zip\"}]}";
  }
  private static final String OLD = release("recovery", 1, "2026-10-10T01:52:36Z");
  private static final String NEW = release("presentation", 2, "2026-10-10T02:45:41Z");
  @Test void latestCompatibleReleaseUsesPublicationTimeInEitherApiOrder() throws Exception {
    for(final String json : new String[]{"[" + OLD + "," + NEW + "]", "[" + NEW + "," + OLD + "]"}) assertEquals("presentation", ReleaseUpdates.select(json, "linux-x64", "", "").orElseThrow().tag());
  }
  @Test void installedOlderReleaseCannotHideTheAvailableUpdate() throws Exception {
    assertEquals("2", ReleaseUpdates.select("[" + OLD + "," + NEW + "]", "linux-x64", "1", "recovery").orElseThrow().assetId());
  }
  @Test void installedNewestReleaseCannotBeOfferedAnOlderVersion() throws Exception {
    assertTrue(ReleaseUpdates.select("[" + OLD + "," + NEW + "]", "linux-x64", "2", "presentation").isEmpty());
  }
  @Test void installedIdentityCannotSuppressInvalidSelectedMetadata() {
    for(final String invalid : new String[]{NEW.replace("sha256:", "invalid:"), NEW.replace("/presentation/", "/recovery/"), NEW.replace("package.zip", "invalid url.zip"), NEW.replace("\"id\":2", "\"id\":0")}) {
      assertThrows(IOException.class, () -> ReleaseUpdates.select("[" + invalid + "]", "linux-x64", "2", "presentation"));
    }
  }
  @Test void newerDraftOrOtherPlatformCannotDisplaceCompatibleRelease() throws Exception {
    assertEquals("recovery", ReleaseUpdates.select("[" + NEW.replace("\"draft\":false", "\"draft\":true") + "," + OLD + "]", "linux-x64", "", "").orElseThrow().tag());
    assertEquals("recovery", ReleaseUpdates.select("[" + NEW.replace("linux-x64.zip", "macos-arm64.zip") + "," + OLD + "]", "linux-x64", "", "").orElseThrow().tag());
  }
  @Test void ambiguityAmongOlderReleasesCannotHideUniqueNewest() throws Exception {
    final String duplicateDate = OLD.replace("recovery", "another-old");
    for(final String json : new String[]{"[" + OLD + "," + duplicateDate + "," + NEW + "]", "[" + NEW + "," + OLD + "," + duplicateDate + "]"}) assertEquals("presentation", ReleaseUpdates.select(json, "linux-x64", "", "").orElseThrow().tag());
  }
  @Test void invalidDatesAmbiguityAndWrongReleaseUrlsFailClosed() {
    assertThrows(IOException.class, () -> ReleaseUpdates.select("[" + NEW.replace("2026-10-10T02:45:41Z", "invalid") + "]", "linux-x64", "", ""));
    assertThrows(IOException.class, () -> ReleaseUpdates.select("[" + OLD + "," + NEW.replace("2026-10-10T02:45:41Z", "2026-10-10T01:52:36Z") + "]", "linux-x64", "", ""));
    assertThrows(IOException.class, () -> ReleaseUpdates.select("[" + NEW.replace("/presentation/", "/recovery/") + "]", "linux-x64", "", ""));
  }
}
