package fmvhd;

import legend.game.modding.events.fmv.FmvPlaybackEvent;
import org.apache.logging.log4j.LogManager;
import org.legendofdragoon.modloader.Mod;
import org.legendofdragoon.modloader.events.EventListener;
import java.io.IOException;
import java.nio.file.Path;
import legend.definitive.fmv.FmvAssetManifest;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import static legend.core.GameEngine.EVENTS;

@Mod(id = "fmvhd", version = "3.0.0")
public final class FmvHdMod {
  private FmvAssetManifest assets;

  public FmvHdMod() {
    try(final var in = FmvHdMod.class.getResourceAsStream("/fmvhd/assets.properties")) {
      this.assets = FmvAssetManifest.read(in);
    } catch(final IOException e) { LogManager.getLogger().warn("FMVHD manifest unavailable; original videos retained", e); }
    EVENTS.register(this);
  }

  /** Shared with the headless resource probe; returns the installed seekable file. */
  public Path installed(final Path artworkRoot, final FmvAssetManifest.Film film) throws IOException {
    final Path data = artworkRoot.toRealPath().resolve("fmvhd");
    if(!Files.isDirectory(data, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(data)) throw new IOException("Invalid FMVHD data directory");
    final Path metadata = data.resolve("manifest.properties");
    if(!Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing FMVHD data manifest");
    try(final var input = Files.newInputStream(metadata, LinkOption.NOFOLLOW_LINKS)) {
      if(this.assets == null || !this.assets.equals(FmvAssetManifest.read(input)) || !film.equals(this.assets.films().get(film.name()))) throw new IOException("FMVHD code/data identity mismatch");
    }
    return VideoCache.installed(artworkRoot, film.source().sha256(), film.source().bytes());
  }

  @EventListener
  public void replace(final FmvPlaybackEvent event) {
    if(event.replacement != null || this.assets == null) return;
    final var film = this.assets.replacement(event.file, event.sourceSha256());
    if(film == null) return;
    try {
      event.replacement = this.installed(Path.of("gfx"), film);
      return;
    } catch(final IOException e) {
      // Embedded data remains a fallback for a legacy manually installed pack.
    }
    try(final var resource = FmvHdMod.class.getResourceAsStream("/fmvhd/videos/" + film.name() + ".mp4")) {
      if(resource == null) return;
      event.replacement = VideoCache.materialize(Path.of("cache", "fmvhd").toAbsolutePath(), film.source().sha256(), film.source().bytes(), resource);
    } catch(final IOException | RuntimeException e) {
      LogManager.getLogger().warn("FMVHD retained original {}: {}", film.name(), e.getMessage());
    }
  }
}
