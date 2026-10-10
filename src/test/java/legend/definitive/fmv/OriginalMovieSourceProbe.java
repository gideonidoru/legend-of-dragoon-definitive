package legend.definitive.fmv;

import legend.core.spu.XaAdpcm;
import legend.game.fmv.Fmv;
import legend.game.unpacker.FileData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

/** Private CPU-only source validation; no original pixels or PCM are saved. */
public final class OriginalMovieSourceProbe {
  private static void samples(final MessageDigest digest, final short[] pcm) {
    final byte[] bytes = new byte[pcm.length * 2];
    for(int i = 0; i < pcm.length; i++) { bytes[i * 2] = (byte)pcm[i]; bytes[i * 2 + 1] = (byte)(pcm[i] >> 8); }
    digest.update(bytes);
  }
  public static void main(final String[] args) throws Exception {
    if(args.length != 1) throw new IllegalArgumentException("Supply private original STR folder");
    int films = 0;
    try(final var sources = Files.list(Path.of(args[0]))) {
      for(final Path path : sources.filter(p -> p.getFileName().toString().endsWith(".IKI")).sorted().toList()) {
        final byte[] bytes = Files.readAllBytes(path);
        final var expected = MessageDigest.getInstance("SHA-256");
        final var observed = MessageDigest.getInstance("SHA-256");
        final XaAdpcm.Decoder decoder = new XaAdpcm.Decoder();
        long expectedSamples = 0, observedSamples = 0;
        int expectedFrames = 0;
        for(int offset = 0; offset < bytes.length; offset += 2352) {
          final int flags = bytes[offset + 18] & 255;
          if((flags & 14) == 4) {
            final short[] pcm = decoder.decode(Arrays.copyOfRange(bytes, offset, offset + 2352), bytes[offset + 19]);
            samples(expected, pcm); expectedSamples += pcm.length / 2;
          }
          if((flags & 14) == 8 && bytes[offset + 28] == 0 && bytes[offset + 29] == 0 && (bytes[offset + 30] != 0 || bytes[offset + 31] != 0)) expectedFrames++;
          if((flags & 128) != 0) break;
        }
        int frames = 0;
        final long deadline = System.nanoTime() + 180_000_000_000L;
        try(final var movie = new OriginalMovie(new FileData(bytes), Fmv::decodeOriginalFrame)) {
          while(!movie.drained()) {
            if(movie.failure() != null) throw movie.failure();
            short[] pcm;
            while((pcm = movie.pollAudio()) != null) { samples(observed, pcm); observedSamples += pcm.length / 2; }
            final var head = movie.peekVideo();
            if(head != null) {
              final var image = movie.pollVideo(head.micros());
              if(image.number() != frames++ || image.height() != 192 || image.width() != 320 && image.width() != 640) throw new AssertionError("Native geometry/frame sequence differs");
            } else Thread.sleep(1);
            if(System.nanoTime() > deadline) throw new AssertionError("Private movie decode timed out");
          }
          if(frames != expectedFrames || observedSamples != expectedSamples || !Arrays.equals(expected.digest(), observed.digest())) throw new AssertionError("Original samples/frames were lost or reordered");
          System.out.println("PASS " + path.getFileName() + " nativeFrames=" + frames + " audioFrames=" + observedSamples + " durationMicros=" + movie.durationMicros() + " geometry=320-or-640x192; ordered PCM including EOF verified");
        }
        films++;
      }
    }
    if(films != 18) throw new AssertionError("Expected all 18 original films, found " + films);
    System.out.println("PASS all18original films decoded through actual native CPU codec; PCM sample counts/order/EOF and original geometry verified; no window or audio output");
  }
}
