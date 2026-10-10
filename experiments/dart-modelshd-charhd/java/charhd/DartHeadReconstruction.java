package charhd;

import com.google.gson.JsonParser;
import legend.game.tmd.TmdFaceDetail;
import legend.game.tmd.TmdGeometryIdentity;
import legend.game.tmd.TmdObjTable1c;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.security.MessageDigest;

/** Exact-geometry UV binding for the gated reconstructed Dart heads. */
final class DartHeadReconstruction {
  private record Binding(String source, String geometry, float[][] uv) { }
  private final Binding field, combat;
  private final BufferedImage image;

  DartHeadReconstruction() throws IOException {
    final byte[] pixels = resource("dart-head-aa-v1.png", 8*1024*1024);
    final String checksum = new String(resource("dart-head-aa-v1.sha256", 65),StandardCharsets.UTF_8).strip();
    this.image = DartFacePaint.read(pixels,checksum);
    if(this.image.getWidth()!=2048 || this.image.getHeight()!=2048) throw new IOException("Reconstructed head texture dimensions differ");
    this.field = binding("field-head-uv.json",checksum);
    this.combat = binding("combat-head-uv.json",checksum);
  }

  private static byte[] resource(final String name, final int budget) throws IOException {
    try(final var input = DartHeadReconstruction.class.getResourceAsStream("/charhd-experiment/reconstruction/"+name)) {
      if(input==null)throw new IOException("Missing reconstructed head resource: "+name);
      final byte[] bytes=input.readNBytes(budget+1);
      if(bytes.length>budget)throw new IOException("Reconstructed head resource exceeds budget");
      return bytes;
    }
  }

  private static Binding binding(final String name, final String textureHash) throws IOException {
    try {
      final byte[] bytes=resource(name,2*1024*1024);
      final String expected=new String(resource(name+".sha256",65),StandardCharsets.UTF_8).strip();
      if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(expected))throw new IOException("Reconstructed head UV checksum mismatch");
      final var root=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
      if(root.get("version").getAsBigDecimal().intValueExact()!=1 || !textureHash.equals(root.get("textureSha256").getAsString()))throw new IOException("Invalid reconstructed head binding");
      final String source=root.get("sourceGeometrySha256").getAsString(),geometry=root.get("geometrySha256").getAsString();
      if(!source.matches("[0-9a-f]{64}") || !geometry.matches("[0-9a-f]{64}"))throw new IOException("Invalid reconstructed head identity");
      final var rows=root.getAsJsonArray("uvs");
      if(rows.isEmpty() || rows.size()>65535)throw new IOException("Invalid reconstructed UV count");
      final float[][] uv=new float[rows.size()][2];
      for(int i=0;i<uv.length;i++) {
        final var row=rows.get(i).getAsJsonArray();
        if(row.size()!=2)throw new IOException("Expected UV pair");
        for(int c=0;c<2;c++) {
          uv[i][c]=row.get(c).getAsFloat();
          if(!Float.isFinite(uv[i][c]) || uv[i][c]<0 || uv[i][c]>1)throw new IOException("Invalid reconstructed UV coordinate");
        }
      }
      return new Binding(source,geometry,uv);
    } catch(final java.security.NoSuchAlgorithmException impossible) {throw new AssertionError(impossible);}
    catch(final RuntimeException malformed) {throw new IOException("Malformed reconstructed head binding",malformed);}
  }

  TmdObjTable1c paint(final String sourceIdentity, final TmdObjTable1c geometry) {
    final Binding binding=this.field.source.equals(sourceIdentity)?this.field:this.combat.source.equals(sourceIdentity)?this.combat:null;
    if(binding==null || geometry.faceDetail()!=null || geometry.vert_top_00.length!=binding.uv.length ||
      !binding.geometry.equals(TmdGeometryIdentity.identity(new TmdObjTable1c[]{geometry})))return null;
    final var primitives=new ArrayList<TmdObjTable1c.Primitive>();
    final float[][] uv=new float[geometry.n_primitive_14][];
    final int[] sources=new int[geometry.n_primitive_14];
    final var surfaces=new legend.core.renderer.SurfaceResponse[geometry.n_primitive_14];
    int rendered=0;
    for(final var primitive:geometry.primitives_10)for(final byte[] packet:primitive.data()) {
      final int mode=primitive.header()>>>24,count=(mode&8)==0?3:4;
      final boolean lit=(mode&1)==0;
      int cursor=(mode&4)!=0?count*4:0;
      if((primitive.header()&0x40000)!=0 || !lit)cursor+=count*4;
      else if((mode&4)==0)cursor+=4;
      uv[rendered]=new float[count*2];
      for(int corner=0;corner<count;corner++) {
        if(lit && ((mode&16)!=0 || corner==0))cursor+=2;
        final int vertex=(packet[cursor]&255)|(packet[cursor+1]&255)<<8;cursor+=2;
        uv[rendered][corner*2]=binding.uv[vertex][0];uv[rendered][corner*2+1]=binding.uv[vertex][1];
      }
      primitives.add(new TmdObjTable1c.Primitive(0,primitive.width(),primitive.header()&~0x02000000,new byte[][]{packet}));
      sources[rendered]=geometry.sourceFace(rendered);surfaces[rendered]=geometry.faceSurface(rendered);rendered++;
    }
    final var result=new TmdObjTable1c("Dart AA reconstructed head experiment",geometry.vert_top_00,geometry.normal_top_08,primitives.toArray(TmdObjTable1c.Primitive[]::new));
    result.sourceFaces(sources,java.util.Arrays.stream(sources).max().orElseThrow()+1);result.faceSurfaces(surfaces);
    final var pixels=ByteBuffer.allocateDirect(this.image.getWidth()*this.image.getHeight()*4);
    for(int y=0;y<this.image.getHeight();y++)for(int x=0;x<this.image.getWidth();x++) {
      final int rgb=this.image.getRGB(x,y);pixels.put((byte)(rgb>>>16)).put((byte)(rgb>>>8)).put((byte)rgb).put((byte)255);
    }
    result.faceDetail(new TmdFaceDetail(pixels.flip(),this.image.getWidth(),this.image.getHeight(),uv));
    return result;
  }
}
