package modelshd;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import legend.game.modding.events.tmd.TmdGeometryEvent;
import legend.game.tmd.TmdObjTable1c;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/** A bounded source-bound part index; no image, script or animation ownership. */
public final class GeometryPass {
  private static final int MAX_BYTES = 16 * 1024 * 1024;
  private final Map<String, Entry> entries;
  private final Resources resources;
  record Entry(String sha256, int bytes) { }
  @FunctionalInterface interface Resources { InputStream open(String identity) throws IOException; }

  public GeometryPass() throws IOException {
    this(ModelsHdMod.class.getResourceAsStream("/modelshd/models/world-pass.json"),
      identity -> ModelsHdMod.class.getResourceAsStream("/modelshd/models/parts/" + identity + ".json"));
  }

  GeometryPass(final InputStream manifest, final Resources resources) throws IOException {
    this.resources = resources;
    if(manifest == null) throw new IOException("Missing ModelsHD world pass index");
    try(manifest) {
      final byte[] bytes = manifest.readNBytes(MAX_BYTES + 1);
      if(bytes.length > MAX_BYTES) throw new IOException("ModelsHD index exceeds budget");
      final var reader = new JsonReader(new java.io.StringReader(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
      reader.setStrictness(Strictness.STRICT);
      reader.setNestingLimit(32);
      final JsonObject root = new GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(reader, JsonObject.class);
      if(reader.peek() != JsonToken.END_DOCUMENT || ModelPack.integer(root.get("format")) != 1) throw new IOException("Unsupported ModelsHD index format");
      final var packs = root.getAsJsonArray("partPacks");
      if(packs.size() > 20000) throw new IOException("ModelsHD index exceeds part count budget");
      final Map<String, Entry> values = new HashMap<>();
      for(final var value : packs) {
        final var part = value.getAsJsonObject();
        final String identity = part.get("sourceGeometrySha256").getAsString();
        final String hash = part.get("packSha256").getAsString();
        final int size = ModelPack.integer(part.get("packBytes"));
        if(!identity.matches("[a-f0-9]{64}") || !hash.matches("[a-f0-9]{64}") || size <= 0 || size > MAX_BYTES
          || values.put(identity, new Entry(hash, size)) != null) throw new IOException("Invalid ModelsHD index entry");
      }
      this.entries = Map.copyOf(values);
    } catch(final RuntimeException failure) {
      throw new IOException("Invalid ModelsHD index", failure);
    }
  }

  public boolean prepare(final TmdGeometryEvent event, final Path overrides) throws IOException {
    if(event.geometry != event.source || event.source.getClass() != TmdObjTable1c.class || event.source.isAuthoredGeometry()) return false;
    final TmdObjTable1c[] source = {event.source};
    final String identity = ModelPack.identity(source);
    final Path override = overrides.resolve(identity + ".json");
    final TmdObjTable1c[] replacement;
    if(Files.isRegularFile(override)) {
      replacement = ModelPack.read(override, source);
    } else {
      final Entry entry = this.entries.get(identity);
      if(entry == null) return false;
      final byte[] payload;
      try(final InputStream input = this.resources.open(identity)) {
        if(input == null) throw new IOException("Missing bundled ModelsHD part");
        payload = input.readNBytes(MAX_BYTES + 1);
      }
      if(payload.length != entry.bytes || !sha256(payload).equals(entry.sha256)) throw new IOException("ModelsHD part checksum mismatch");
      replacement = ModelPack.read(new ByteArrayInputStream(payload), source);
    }
    event.geometry = replacement[0];
    return true;
  }

  private static String sha256(final byte[] bytes) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch(final NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
  }
}
