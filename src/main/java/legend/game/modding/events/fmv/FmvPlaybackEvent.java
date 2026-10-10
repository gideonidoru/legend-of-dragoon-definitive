package legend.game.modding.events.fmv;

import org.legendofdragoon.modloader.events.Event;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Mods may provide a locally readable replacement before playback mutates renderer state. */
public final class FmvPlaybackEvent extends Event {
  public final String file;
  private final byte[] original;
  public Path replacement;

  public FmvPlaybackEvent(final String file, final byte[] original) {
    this.file = file;
    this.original = original;
  }

  public String sourceSha256() {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(this.original));
    } catch(final NoSuchAlgorithmException e) { throw new AssertionError(e); }
  }
}
