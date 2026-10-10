// Private source binding probe (2026-10-10), AGPL v3; writes no game payload.
package legend.definitive.effects;

import fxhd.FxHdMod;
import legend.core.gpu.Gpu;
import legend.game.tim.Tim;
import legend.game.unpacker.FileData;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opt-in CPU/source check; this is not a rendered game or physical Deck acceptance. */
public final class FxHdSourceProbe {
  public static void main(final String[] args) throws Exception {
    final Path files = Path.of(args[0]);
    for(final String name : FxHdResourcesTest.NAMES) {
      final String image = name.equals("smoke_2_dust_palette") ? "smoke_2" : name;
      final String palette = name.equals("smoke_2_dust_palette") ? "dust" : image;
      final byte[] source = EffectArtwork.withPalette(Files.readAllBytes(files.resolve("SUBMAP/" + image + ".tim")), Files.readAllBytes(files.resolve("SUBMAP/" + palette + ".tim")));
      final var replacement = FxHdMod.read(name, source);
      final Tim tim = new Tim(new FileData(source));
      final int u = switch(name) { case "left_foot" -> 96; case "right_foot" -> 112; case "savepoint_big_circle" -> 160; default -> 64; };
      final int v = switch(name) { case "smoke_1" -> 32; case "smoke_2_dust_palette", "savepoint_big_circle" -> 64; default -> 0; };
      final int clut = tim.getClutRect().y << 6 | tim.getClutRect().x / 16;
      if(!EffectArtwork.bindingMatches(tim, 31, clut, u, v)) throw new AssertionError("Native binding differs: " + name);
      final Gpu gpu = new Gpu();
      gpu.uploadData15(tim.getImageRect(), tim.getImageData()); gpu.uploadData15(tim.getClutRect(), tim.getClutData());
      try(final EffectArtwork artwork = EffectArtworkTest.owned(source)) {
        if(!artwork.matchesNative(gpu)) throw new AssertionError("VRAM controls differ: " + name);
      }
      System.out.println(name + ": source/hash/palette/visibility/native-address/CPU-VRAM passed; " + replacement.width + "x" + replacement.height);
    }
  }
}
