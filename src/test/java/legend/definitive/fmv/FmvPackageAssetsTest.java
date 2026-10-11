package legend.definitive.fmv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

final class FmvPackageAssetsTest {
  @TempDir Path root;
  private Properties inventory() throws Exception {
    final Properties p = new Properties();
    try(final var paths=Files.walk(this.root)) {
      for(final Path file : paths.filter(Files::isRegularFile).toList()) p.setProperty(this.root.relativize(file).toString().replace('\\','/'),FmvSplitFixture.sha(Files.readAllBytes(file)));
    }
    return p;
  }
  @Test void completeMoviesAndSmallCodeMustAgree() throws Exception {
    final var fixture = new FmvSplitFixture(); fixture.split(this.root,"small code");
    FmvPackageAssets.verify(this.root,inventory());
    final var changed=fixture.manifest(false); changed.setProperty("OPENH.sourceSha256","0".repeat(64));
    Files.write(this.root.resolve(FmvAssetManifest.FILE),FmvSplitFixture.bytes(changed));
    assertThrows(IOException.class, () -> FmvPackageAssets.verify(this.root,inventory()));
  }
  @Test void missingExtraWrongHashAndWrongSizeFailClosed() throws Exception {
    final var fixture=new FmvSplitFixture();fixture.split(this.root,"small code");
    final var assets=FmvAssetManifest.read(new java.io.ByteArrayInputStream(FmvSplitFixture.bytes(fixture.manifest(false))));
    final String movie=assets.films().get("OPENH").path();
    final var missing=inventory();missing.remove(movie);assertThrows(IOException.class,()->FmvPackageAssets.verify(this.root,missing));
    final var extra=inventory();extra.setProperty("gfx/fmvhd/videos/unapproved.mp4","a".repeat(64));assertThrows(IOException.class,()->FmvPackageAssets.verify(this.root,extra));
    final var wrong=inventory();wrong.setProperty(movie,"0".repeat(64));assertThrows(IOException.class,()->FmvPackageAssets.verify(this.root,wrong));
    Files.write(this.root.resolve(movie),new byte[]{1});assertThrows(IOException.class,()->FmvPackageAssets.verify(this.root,inventory()));
  }
  @Test void attributionAndEmbeddedVideoDuplicationAreRejected() throws Exception {
    final var fixture=new FmvSplitFixture();fixture.split(this.root,"small code");
    Files.writeString(this.root.resolve("gfx/fmvhd/NOTICE.txt"),"changed notice");assertThrows(IOException.class,()->FmvPackageAssets.verify(this.root,inventory()));
    Files.write(this.root.resolve("gfx/fmvhd/NOTICE.txt"),fixture.notice);fixture.jar(this.root,true,"giant legacy code");
    assertThrows(IOException.class,()->FmvPackageAssets.verify(this.root,inventory()));
  }
}
