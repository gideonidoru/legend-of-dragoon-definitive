// Definitive private preparation (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.tools;

import legend.game.unpacker.Unpacker;
import java.nio.file.*;

/** Runs the upstream extraction pipeline without starting video, audio or a game window. */
public final class PrepareDiscs {
  private PrepareDiscs() { }
  public static void main(final String[] args) throws Exception {
    final var language = new java.util.Properties();
    try(final var reader = Files.newBufferedReader(Path.of("lang/unpacker.en.lang"))) { language.load(reader); }
    final var translations = new java.util.HashMap<String, String>();
    for(final String key : language.stringPropertyNames()) translations.put(key, language.getProperty(key));
    legend.core.GameEngine.addLangOverrides(translations);
    Unpacker.setStatusListener(status -> System.out.println("DEFINITIVE_STATUS\t" + status));
    Unpacker.unpack();
    if(!Files.isRegularFile(Path.of("files/version"))) throw new java.io.IOException("Disc preparation did not finish. Retry preparation; original disc images are unchanged.");
    for(int archive = 0; archive < 4; archive++) {
      if(legend.core.audio.xa.XaTranscoder.needsConversion(Path.of("files/XA/LODXA0" + archive + ".XA"))) {
        throw new java.io.IOException("Lossless audio preparation is incomplete. Retry preparation; original disc images are unchanged.");
      }
    }
    System.out.println("Private disc preparation complete.");
  }
}
