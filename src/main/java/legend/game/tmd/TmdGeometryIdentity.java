// Shared source geometry identity, AGPL v3; see LICENSE.
package legend.game.tmd;
import org.joml.Vector3f;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Canonical geometry identity shared across isolated mod class loaders. */
public final class TmdGeometryIdentity {
  private TmdGeometryIdentity() { }
  private record Face(int header, byte[] packet) { }
  public static String identity(final TmdObjTable1c[] parts) {
    try {
      final MessageDigest digest = MessageDigest.getInstance("SHA-256");
      final ByteBuffer value = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
      put(digest, value, parts.length);
      for(final TmdObjTable1c part : parts) {
        put(digest, value, part.scale_18);
        for(final Vector3f[] vectors : new Vector3f[][] {part.vert_top_00, part.normal_top_08}) {
          put(digest, value, vectors.length);
          for(final Vector3f vector : vectors) {
            put(digest, value, Float.floatToIntBits(vector.x));
            put(digest, value, Float.floatToIntBits(vector.y));
            put(digest, value, Float.floatToIntBits(vector.z));
          }
        }
        final List<Face> faces = faces(part);
        put(digest, value, faces.size());
        for(final Face face : faces) {
          put(digest, value, face.header & 0xff04_0000);
          // UV/CLUT/page relocation does not change geometry identity.
          final int mode = face.header >>> 24;
          final int corners = (mode & 8) == 0 ? 3 : 4;
          final boolean lit = (mode & 1) == 0;
          final boolean shaded = (face.header & 0x40000) != 0;
          int cursor = (mode & 4) != 0 ? corners * 4 : 0;
          if(shaded || !lit) cursor += corners * 4;
          else if((mode & 4) == 0) cursor += 4;
          for(int i = 0; i < corners; i++) {
            if(lit && ((mode & 16) != 0 || i == 0)) { put(digest, value, u16(face.packet, cursor)); cursor += 2; }
            put(digest, value, u16(face.packet, cursor)); cursor += 2;
          }
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch(final NoSuchAlgorithmException e) { throw new AssertionError(e); }
  }

  private static void put(final MessageDigest digest, final ByteBuffer value, final int number) {
    value.clear(); value.putInt(number); digest.update(value.array());
  }

  private static List<Face> faces(final TmdObjTable1c table) {
    final List<Face> result = new ArrayList<>();
    for(final var primitive : table.primitives_10) for(final byte[] packet : primitive.data()) result.add(new Face(primitive.header(), packet));
    return result;
  }

  private static int u16(final byte[] bytes, final int offset) {
    return (bytes[offset] & 255) | (bytes[offset + 1] & 255) << 8;
  }
}
