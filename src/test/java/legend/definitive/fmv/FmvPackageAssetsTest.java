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
  @Test void allExternalDataRemovedStillRejectsSplitModule() throws Exception {
    final var fixture = new FmvSplitFixture(); fixture.split(this.root, "small code");
    try(final var paths = Files.walk(this.root.resolve("gfx/fmvhd"))) {
      for(final Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
    }
    assertThrows(IOException.class, () -> FmvPackageAssets.verify(this.root, inventory()));
  }
  @Test void genuineEmbeddedLegacyAndNoMovieModuleRemainValid() throws Exception {
    FmvPackageAssets.verify(this.root, inventory());
    final var fixture = new FmvSplitFixture(); fixture.jar(this.root, true, "genuine legacy");
    FmvPackageAssets.verify(this.root, inventory());
    try(final var source = new java.util.zip.ZipFile(this.root.resolve("bundled-mods/FMVHD-v0.1.0.jar").toFile());
        final var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(this.root.resolve("missing.jar")))) {
      final var items = source.entries();
      while(items.hasMoreElements()) {
        final var item = items.nextElement(); if(item.getName().equals("fmvhd/videos/OPENH.mp4")) continue;
        output.putNextEntry(new java.util.zip.ZipEntry(item.getName())); try(final var input = source.getInputStream(item)) { input.transferTo(output); } output.closeEntry();
      }
    }
    Files.move(this.root.resolve("missing.jar"), this.root.resolve("bundled-mods/FMVHD-v0.1.0.jar"), StandardCopyOption.REPLACE_EXISTING);
    assertThrows(IOException.class, () -> FmvPackageAssets.verify(this.root, inventory()));
  }
}
