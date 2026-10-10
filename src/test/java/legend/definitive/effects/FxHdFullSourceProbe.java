package legend.definitive.effects;

import com.google.gson.JsonParser;
import fxhd.FxHdMod;
import legend.definitive.textures.TexturePilot;
import legend.game.tim.Tim;
import legend.game.unpacker.FileData;

import java.nio.file.Files;
import java.nio.file.Path;

/** Opt-in full private source check; no GPU, window, audio or original asset publication. */
public final class FxHdFullSourceProbe {
  public static void main(final String[] args) throws Exception {
    final Path files = Path.of(args[0]).toRealPath();
    final var ledger = JsonParser.parseString(Files.readString(Path.of("integrations/fxhd/production/full-coverage.json"))).getAsJsonObject();
    final var gpu = new legend.core.gpu.Gpu();
    int checked = 0;
    for(final var item : ledger.getAsJsonArray("sources")) {
      final var row = item.getAsJsonObject(); final Path source = files.resolve(row.get("source").getAsString()).normalize();
      if(!source.startsWith(files)) throw new IllegalArgumentException("FX source path escapes extraction");
      final byte[] bytes = Files.readAllBytes(source);
      final String hash = TexturePilot.sha256(bytes);
      if(!hash.equals(row.get("sourceSha256").getAsString())) throw new AssertionError("Source changed: " + source);
      final Tim tim = new Tim(new FileData(bytes)); final var rect = tim.getImageRect();
      final var detail = FxHdMod.readIndexed(hash, rect.w * 4, rect.h);
      if(detail == null) throw new AssertionError("Uncovered effect texture: " + source);
      IndexedEffectVram.validate(tim, detail);
      gpu.uploadEffectTim(tim, event -> event.detail = detail);
      checked++;
    }
    if(checked != ledger.get("sourceCount").getAsInt()) throw new AssertionError("Incomplete full source probe");
    System.out.println("PASS: every " + checked + " private FX source has source-hash-bound, exact-index, bounded 2x live-palette detail through actual atomic CPU VRAM uploads. Native gameplay/Deck acceptance remains separate.");
  }
}
