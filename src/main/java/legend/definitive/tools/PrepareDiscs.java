// Definitive private preparation (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.tools;

import legend.game.unpacker.Unpacker;
import java.nio.file.*;

/** Runs the upstream extraction pipeline without starting video, audio or a game window. */
public final class PrepareDiscs {
  private PrepareDiscs() { }
  public static void main(final String[] args) throws Exception {
    Unpacker.setStatusListener(System.out::println);
    Unpacker.unpack();
    if(!Files.isRegularFile(Path.of("files/version"))) throw new java.io.IOException("Disc preparation did not finish. Retry preparation; original disc images are unchanged.");
    System.out.println("Private disc preparation complete.");
  }
}
