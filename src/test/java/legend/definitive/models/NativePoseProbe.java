// Headless authoring probe (2026-10-10), AGPL v3; see LICENSE. Never packaged as a runtime feature.
package legend.definitive.models;

import legend.core.gte.GsCOORDINATE2;
import legend.core.gte.ModelPart10;
import legend.game.Models;
import legend.game.types.Model124;
import legend.game.types.TmdAnimationFile;
import legend.game.unpacker.FileData;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Inspect the actual animation methods without meshes, textures, GPU, scripts or a window. */
public final class NativePoseProbe {
  private NativePoseProbe() { }
  public record Frame(int tick, int keyframeCursor, int subframe, float[] matrices) {
    public Frame { matrices = matrices.clone(); }
    @Override public float[] matrices() { return this.matrices.clone(); }
  }
  public record Profile(String route, int framesPerKeyframe, List<Frame> samples) {
    public Profile { samples = List.copyOf(samples); }
  }

  static int parts(final byte[] bytes) {
    if(bytes.length < 16 || bytes.length > 1024 * 1024) throw new IllegalArgumentException("Animation size is outside the probe budget");
    final var input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    final int parts = Short.toUnsignedInt(input.getShort(12)), frames = Short.toUnsignedInt(input.getShort(14));
    if(input.getInt(0) != 12 || parts < 1 || parts > 256 || frames < 2 || frames > 512 || (frames & 1) != 0 || bytes.length != 16 + frames / 2 * parts * 12) throw new IllegalArgumentException("Expected bounded standard TMD animation records");
    if((long)parts * (frames / 2) * 8 * 12 > 1_000_000) throw new IllegalArgumentException("Combined pose output exceeds the probe budget");
    return parts;
  }

  static List<Profile> inspect(final byte[] input) {
    final byte[] bytes = input.clone();
    final int count = parts(bytes);
    final var profiles = new ArrayList<Profile>();
    for(final String route : List.of("combat-2", "combat-4", "animation-apply-2")) {
      final int stride = route.equals("combat-4") ? 4 : 2;
      final var animation = new TmdAnimationFile(new FileData(bytes));
      final var model = new Model124("headless source-motion probe");
      model.modelParts_00 = new ModelPart10[count];
      for(int part = 0; part < count; part++) {
        final var value = new ModelPart10(); value.coord2_04 = new GsCOORDINATE2(); model.modelParts_00[part] = value;
      }
      // Seed the actual loader's first pose. The probe has no textures, CLUTs,
      // objects, scripts or renderer.
      Models.loadModelStandardAnimation(model, animation);
      final var samples = new ArrayList<Frame>();
      for(int tick = 0; tick < animation.partTransforms_10.length * stride; tick++) {
        if(route.startsWith("combat")) Models.animateModel(model, stride);
        else animation.apply(model, tick);
        final float[] matrices = new float[count * 12];
        for(int part = 0; part < count; part++) {
          final var value = model.modelParts_00[part].coord2_04.coord;
          final int offset = part * 12;
          final float[] rows = {value.m00(), value.m10(), value.m20(), value.transfer.x,
            value.m01(), value.m11(), value.m21(), value.transfer.y,
            value.m02(), value.m12(), value.m22(), value.transfer.z};
          for(int field = 0; field < 12; field++) {
            if(!Float.isFinite(rows[field])) throw new IllegalStateException("Nonfinite source animation transform");
            matrices[offset + field] = rows[field];
          }
        }
        samples.add(new Frame(tick, model.currentKeyframe_94, model.subFrameIndex, matrices));
      }
      profiles.add(new Profile(route, stride, samples));
    }
    return List.copyOf(profiles);
  }

  public static void main(final String[] args) throws Exception {
    if(args.length != 2) throw new IllegalArgumentException("Supply a private source animation and a new private JSON output path");
    System.setProperty("java.awt.headless", "true");
    final Path input = Path.of(args[0]);
    if(!Files.isRegularFile(input) || Files.size(input) > 1024 * 1024) throw new IOException("Expected a bounded private animation file");
    final byte[] bytes;
    try(final var stream = Files.newInputStream(input)) { bytes = stream.readNBytes(1024 * 1024 + 1); }
    final int count = parts(bytes);
    final Path requested = Path.of(args[1]).toAbsolutePath().normalize();
    if(requested.getParent() == null || requested.getFileName() == null) throw new IOException("Supply a new report filename inside an existing private folder");
    final Path parent = requested.getParent().toRealPath();
    final Path output = parent.resolve(requested.getFileName());
    final Path sourceRoot = Path.of(System.getProperty("definitive.sourceRoot", ".")).toRealPath();
    if(output.startsWith(sourceRoot)) throw new IOException("Keep parsed game motion outside the source checkout");
    final var profiles = inspect(bytes);
    final StringBuilder text = new StringBuilder("{\"pipeline\":\"definitive-private-native-pose-1\",\"scope\":\"Actual source animation methods; no renderer, collision, gameplay or device acceptance\",\"matrixOrder\":\"row-major 3x4 source units\",\"sourceSha256\":\"");
    text.append(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))).append("\",\"parts\":").append(count).append(",\"keys\":").append((bytes.length - 16) / (count * 12)).append(",\"profiles\":[");
    for(int profile = 0; profile < profiles.size(); profile++) {
      if(profile != 0) text.append(',');
      final var value = profiles.get(profile);
      text.append("{\"route\":\"").append(value.route()).append("\",\"framesPerKeyframe\":").append(value.framesPerKeyframe()).append(",\"samples\":[");
      for(int sample = 0; sample < value.samples().size(); sample++) {
        if(sample != 0) text.append(',');
        final var frame = value.samples().get(sample);
        text.append("{\"tick\":").append(frame.tick()).append(",\"keyframeCursor\":").append(frame.keyframeCursor()).append(",\"subframe\":").append(frame.subframe()).append(",\"matrices\":[");
        final float[] matrices = frame.matrices();
        for(int field = 0; field < matrices.length; field++) { if(field != 0) text.append(','); text.append(matrices[field]); }
        text.append("]}");
      }
      text.append("]}");
    }
    text.append("]}\n");
    if(text.length() > 32 * 1024 * 1024) throw new IOException("Pose report exceeds its output budget");
    final Path stage = Files.createTempFile(parent, ".definitive-private-poses-", ".tmp");
    try {
      Files.writeString(stage, text, StandardCharsets.UTF_8);
      // Complete-file publication without overwriting an existing report or link.
      Files.createLink(output, stage);
    } finally { Files.deleteIfExists(stage); }
  }
}
