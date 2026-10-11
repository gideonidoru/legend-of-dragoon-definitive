package legend.definitive.fmv;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

final class FmvAssetManifestTest {
  private static FmvAssetManifest read(final Properties p) throws Exception { return FmvAssetManifest.read(new ByteArrayInputStream(FmvSplitFixture.bytes(p))); }
  @Test void allEighteenFilmsRequireTheMatchingNativeSource() throws Exception {
    final var fixture = new FmvSplitFixture(); final var manifest = read(fixture.manifest(false));
    assertEquals(18, manifest.films().size());
    for(final var film : manifest.films().values()) {
      assertEquals(film, manifest.replacement("STR/" + film.name() + ".IKI", film.source().sourceSha256()));
      assertNull(manifest.replacement("STR/" + film.name() + ".IKI", "0".repeat(64)));
    }
    assertNull(manifest.replacement("STR/../OPENH.IKI", "a".repeat(64)));
    assertNull(manifest.replacement("STR/UNKNOWN.IKI", "a".repeat(64)));
  }
  @Test void incompleteExtraDuplicateAndOversizedManifestsAreRejected() throws Exception {
    final var fixture = new FmvSplitFixture(); final var missing = fixture.manifest(false); missing.remove("ENDING2H.bytes");
    assertThrows(IOException.class, () -> read(missing));
    final var extra = fixture.manifest(false); extra.setProperty("UNAPPROVED.sha256", "a".repeat(64)); assertThrows(IOException.class, () -> read(extra));
    final byte[] original = FmvSplitFixture.bytes(fixture.manifest(false));
    final byte[] duplicate = (new String(original, StandardCharsets.UTF_8) + "OPENH.bytes=1\n").getBytes(StandardCharsets.UTF_8);
    assertThrows(IOException.class, () -> FmvAssetManifest.read(new ByteArrayInputStream(duplicate)));
    assertThrows(IOException.class, () -> FmvAssetManifest.read(new ByteArrayInputStream(new byte[65537])));
  }
  @Test void timingDimensionsBoundsAndPathDigestsCannotBeChangedSilently() throws Exception {
    final var fixture = new FmvSplitFixture();
    for(final String[] invalid : new String[][] {{"OPENH.fps","30"},{"OPENH.duration","2"},{"OPENH.width","640"},{"OPENH.bytes","536870913"},{"OPENH.sha256","../outside"},{"OPENH.frames","0"}}) {
      final var p = fixture.manifest(false); p.setProperty(invalid[0],invalid[1]); assertThrows(IOException.class, () -> read(p), invalid[0]);
    }
  }
  @Test void samePayloadMayBeSharedWithoutDroppingEitherSourceBinding() throws Exception {
    final var fixture = new FmvSplitFixture(); fixture.movies.put("DEMOH",fixture.movies.get("OPENH"));
    final var manifest = read(fixture.manifest(false)); assertEquals(18,manifest.films().size());
    assertEquals(manifest.films().get("OPENH").path(),manifest.films().get("DEMOH").path());
    assertNotEquals(manifest.films().get("OPENH").source().sourceSha256(),manifest.films().get("DEMOH").source().sourceSha256());
  }
}
