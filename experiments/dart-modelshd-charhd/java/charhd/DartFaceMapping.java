package charhd;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** One bounded mapping resource shared by the offline study and native appearance module. */
record DartFaceMapping(Set<Integer> fieldFaces, Set<Integer> combatFaces, double projection,
                       double originY, double combatScale, double combatOriginY) {
  static DartFaceMapping load() {
    try(final var input=DartFaceMapping.class.getResourceAsStream("/charhd-experiment/dart-face-mapping-v1.json")) {
      if(input==null)throw new IOException("Missing face mapping");
      final byte[] bytes=input.readNBytes(8193);
      if(bytes.length>8192)throw new IOException("Face mapping exceeds budget");
      final JsonObject root=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
      if(root.get("version").getAsBigDecimal().intValueExact()!=1)throw new IOException("Unknown face mapping version");
      final double size=positive(root,"templateSize"),fit=positive(root,"templateFit"),span=positive(root,"templateSpan");
      if(size>2048 || fit>size)throw new IOException("Invalid template dimensions");
      return new DartFaceMapping(faces(root,"fieldFaces"),faces(root,"combatFaces"),fit/span/size,
        finite(root,"originY"),positive(root,"combatScale"),finite(root,"combatOriginY"));
    } catch(final IOException failure) {throw new UncheckedIOException(failure);}
    catch(final RuntimeException failure) {throw new UncheckedIOException(new IOException("Invalid face mapping",failure));}
  }
  private static double finite(final JsonObject root,final String key)throws IOException {
    final double value=root.get(key).getAsDouble();
    if(!Double.isFinite(value)||Math.abs(value)>10000)throw new IOException("Invalid mapping coordinate");
    return value;
  }
  private static double positive(final JsonObject root,final String key)throws IOException {
    final double value=finite(root,key);
    if(value<.0001)throw new IOException("Invalid mapping scale");
    return value;
  }
  private static Set<Integer> faces(final JsonObject root,final String key)throws IOException {
    final var values=root.getAsJsonArray(key);
    if(values.isEmpty()||values.size()>50000)throw new IOException("Invalid face selection size");
    final Set<Integer> result=new HashSet<>();
    for(final var element:values) {
      final int face=element.getAsBigDecimal().intValueExact();
      if(face<0||face>=50000||!result.add(face))throw new IOException("Invalid or repeated selected face");
    }
    return Set.copyOf(result);
  }
}
