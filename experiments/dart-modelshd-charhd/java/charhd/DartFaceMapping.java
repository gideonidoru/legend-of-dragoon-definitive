package charhd;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/** One bounded mapping resource shared by the offline study and native appearance module. */
record DartFaceMapping(Set<Integer> fieldFaces, Set<Integer> combatFaces, double projection,
                       double originY, double combatScale, double combatOriginY,
                       double combatFaceOriginY, double combatFaceVerticalScale,
                       Set<Integer> fieldHairFaces, Set<Integer> combatHairFaces,
                       double faceRegionHeight, double hairRegionWidth,
                       Map<Integer,HairProjection> fieldHairProjection, Map<Integer,HairProjection> combatHairProjection) {
  static DartFaceMapping load() {
    try(final var input=DartFaceMapping.class.getResourceAsStream("/charhd-experiment/dart-face-mapping-v1.json")) {
      if(input==null)throw new IOException("Missing face mapping");
      final byte[] bytes=input.readNBytes(65537);
      if(bytes.length>65536)throw new IOException("Face mapping exceeds budget");
      final JsonObject root=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
      if(root.get("version").getAsBigDecimal().intValueExact()!=1)throw new IOException("Unknown face mapping version");
      final double size=positive(root,"templateSize"),fit=positive(root,"templateFit"),span=positive(root,"templateSpan");
      if(size>2048 || fit>size)throw new IOException("Invalid template dimensions");
      return new DartFaceMapping(faces(root,"fieldFaces"),faces(root,"combatFaces"),fit/span/size,
        finite(root,"originY"),positive(root,"combatScale"),finite(root,"combatOriginY"),
        finite(root,"combatFaceOriginY"),positive(root,"combatFaceVerticalScale"),
        faces(root,"fieldHairFaces"),faces(root,"combatHairFaces"),unit(root,"faceRegionHeight"),unit(root,"hairRegionWidth"),
        projections(root,"fieldHairProjection",faces(root,"fieldHairFaces")),
        projections(root,"combatHairProjection",faces(root,"combatHairFaces")));
    } catch(final IOException failure) {throw new UncheckedIOException(failure);}
    catch(final RuntimeException failure) {throw new UncheckedIOException(new IOException("Invalid face mapping",failure));}
  }
  record HairProjection(double ux,double uy,double uz,double u0,double vx,double vy,double vz,double v0) {
    double u(double x,double y,double z) {return Math.clamp(ux*x+uy*y+uz*z+u0,0,1)*.996+.002;}
    double v(double x,double y,double z) {return Math.clamp(vx*x+vy*y+vz*z+v0,0,1)*.996+.002;}
  }
  private static Map<Integer,HairProjection> projections(final JsonObject root,final String key,final Set<Integer> faces)throws IOException {
    final var entries=root.getAsJsonObject(key);final Map<Integer,HairProjection> result=new HashMap<>();
    if(entries.size()!=faces.size())throw new IOException("Hair projection count differs");
    for(final var entry:entries.entrySet()) {
      final int face=Integer.parseInt(entry.getKey());final var values=entry.getValue().getAsJsonArray();
      if(!faces.contains(face)||values.size()!=8)throw new IOException("Invalid hair projection");
      final double[] v=new double[8];
      for(int i=0;i<8;i++){v[i]=values.get(i).getAsDouble();if(!Double.isFinite(v[i])||Math.abs(v[i])>10000)throw new IOException("Invalid hair projection coefficient");}
      if(result.put(face,new HairProjection(v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7]))!=null)throw new IOException("Repeated hair projection");
    }
    return Map.copyOf(result);
  }
  private static double unit(final JsonObject root,final String key)throws IOException {
    final double value=positive(root,key);
    if(value>=1)throw new IOException("Invalid atlas region");
    return value;
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
