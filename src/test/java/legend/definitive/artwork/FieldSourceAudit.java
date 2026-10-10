package legend.definitive.artwork;

import com.google.gson.JsonParser;
import legend.core.gpu.Rect4i;
import legend.core.gpu.VramTextureLoader;
import legend.core.gpu.VramTextureSingle;
import legend.game.submap.EnvironmentFile;
import legend.game.tim.Tim;
import legend.game.unpacker.FileData;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Actual native field palette/canvas decoders compared with the private Python census. No renderer. */
public final class FieldSourceAudit {
  private record Source(Map<Integer,byte[]> files, List<Tim> textures) { }
  private static byte[] bounded(final Path path, final int max) throws Exception {
    try(final var stream=Files.newInputStream(path)) {
      final byte[] bytes=stream.readNBytes(max+1);
      if(bytes.length>max) throw new IllegalArgumentException("Source budget");
      return bytes;
    }
  }
  private static String hash(final byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }
  private static Source source(final Path folder) throws Exception {
    final Map<Integer,int[]> aliases=new LinkedHashMap<>();
    for(final String line:new String(bounded(folder.resolve("mrg"),65536),java.nio.charset.StandardCharsets.US_ASCII).split("\\R")) {
      final var match=java.util.regex.Pattern.compile("(\\d+)=(\\d*);(\\d+)").matcher(line);
      if(!match.matches()) throw new IllegalArgumentException("MRG syntax");
      final int index=Integer.parseInt(match.group(1)), target=match.group(2).isEmpty()?-1:Integer.parseInt(match.group(2));
      if(aliases.put(index,new int[]{target,Integer.parseInt(match.group(3))})!=null) throw new IllegalArgumentException("Duplicate MRG entry");
    }
    final Map<Integer,byte[]> files=new LinkedHashMap<>();
    for(final int index:aliases.keySet().stream().sorted().toList()) {
      int target=aliases.get(index)[0];
      if(target==-1) {if(aliases.get(index)[1]!=0) throw new IllegalArgumentException("Missing required source");continue;}
      final var seen=new HashSet<Integer>();
      while(aliases.containsKey(target) && aliases.get(target)[0]!=target) {
        if(!seen.add(target)) throw new IllegalArgumentException("Alias cycle");
        target=aliases.get(target)[0];
      }
      if(!aliases.containsKey(target)) throw new IllegalArgumentException("Unresolved alias");
      files.put(index,bounded(folder.resolve(Integer.toString(target)),1024*1024));
    }
    final List<Tim> textures=new ArrayList<>();
    for(final var entry:files.entrySet()) if(entry.getKey()>=3) textures.add(new Tim(new FileData(entry.getValue())));
    return new Source(files,textures);
  }
  private static String fingerprint(final int w,final int h,final int[] pixels) throws Exception {
    final ByteBuffer bytes=ByteBuffer.allocate(8+w*h*4).order(ByteOrder.LITTLE_ENDIAN);
    bytes.putInt(w).putInt(h);
    for(final int colour:pixels) bytes.putInt(colour);
    return hash(bytes.array());
  }
  private static boolean opaque(final int[] pixels) {
    for(final int colour:pixels) if((colour>>>24)!=0) return true;
    return false;
  }
  public static void main(final String[] args) throws Exception {
    if(args.length!=2) throw new IllegalArgumentException("Requires private extraction and private census");
    final Path files=Path.of(args[0]);
    final var report=JsonParser.parseString(new String(bounded(Path.of(args[1]),32*1024*1024),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
    if(!hash(bounded(files.resolve("SUBMAP/NEWROOT.RDT"),1024*1024)).equals(report.get("newRootSha256").getAsString())) throw new IllegalStateException("NEWROOT changed");
    final var unique=new HashSet<String>();
    int scenes=0,visible=0,empty=0,missing=0;
    for(final var value:report.getAsJsonArray("renders")) {
      final var expected=value.getAsJsonObject();final var route=expected.getAsJsonObject("representative");
      final int cut=route.get("cut").getAsInt();
      final Source source=source(files.resolve("SECT/DRGN"+route.get("bank").getAsInt()+".BIN/"+route.get("directory").getAsInt()));
      for(final var entry:expected.getAsJsonArray("sourceFiles")) {
        final var e=entry.getAsJsonObject();final byte[] data=source.files.get(e.get("index").getAsInt());
        if(data==null || data.length!=e.get("size").getAsInt() || !hash(data).equals(e.get("sha256").getAsString())) throw new IllegalStateException("Field source changed");
      }
      final var env=new EnvironmentFile(new FileData(source.files.get(0)));
      final Tim[] tims=new Tim[env.allTextureCount_14];final Rect4i[] rects=new Rect4i[tims.length];
      for(int i=0;i<tims.length;i++) {
        final var slot=env.environments_18[i];
        if((cut==111 && i==8)||(cut==288 && i==17)||(cut==595 && i==5)) slot.textureOffsetY_12++;
        if(cut==642 && i==2) slot.vramPos_08.w--;
        for(final Tim texture:source.textures) if(texture.getImageRect().contains((slot.tpage_20&15)*64,(slot.tpage_20&16)!=0?256:0)) {
          tims[i]=texture;rects[i]=new Rect4i(slot.textureOffsetX_10,slot.textureOffsetY_12,slot.vramPos_08.w,slot.vramPos_08.h);break;
        }
      }
      final Rect4i canvas=Rect4i.bound(rects);final var expectedRect=expected.getAsJsonArray("canvasRect");
      if(canvas.x!=expectedRect.get(0).getAsInt() || canvas.y!=expectedRect.get(1).getAsInt() || canvas.w!=expectedRect.get(2).getAsInt() || canvas.h!=expectedRect.get(3).getAsInt()) throw new IllegalStateException("Field canvas differs");
      final int[] background=new int[canvas.w*canvas.h];final List<int[]> layers=new ArrayList<>();final List<Rect4i> sizes=new ArrayList<>();
      for(int i=0;i<tims.length;i++) {
        if(tims[i]==null) {missing++;if(i>=env.backgroundTextureCount_15) {layers.add(null);sizes.add(null);}continue;}
        final var slot=env.environments_18[i];final VramTextureSingle texture=VramTextureLoader.textureFromTim(tims[i]);
        final VramTextureSingle palette=VramTextureLoader.palettesFromTim(tims[i])[0];
        final Rect4i region=new Rect4i(slot.vramPos_08);
        if(i>=env.backgroundTextureCount_15) {region.w=Math.min(region.w,texture.rect.w-region.x);region.h=Math.min(region.h,texture.rect.h-region.y);}
        final int[] pixels=texture.applyPalette(palette,region);
        for(int j=0;j<pixels.length;j++) if(pixels[j]!=0) pixels[j]|=0xff000000;
        if(i<env.backgroundTextureCount_15) for(int y=0;y<region.h;y++) System.arraycopy(pixels,y*region.w,background,(rects[i].y-canvas.y+y)*canvas.w+rects[i].x-canvas.x,region.w);
        else {layers.add(pixels);sizes.add(region);}
      }
      final var records=expected.getAsJsonArray("images");
      for(int i=0;i<records.size();i++) {
        final var record=records.get(i).getAsJsonObject();final int[] pixels=i==0?background:layers.get(i-1);
        if(pixels==null || !opaque(pixels)) {
          if(record.has("decodedRgbaSha256")) throw new IllegalStateException("Native empty layer claimed visible");
          if(pixels!=null) empty++;
          continue;
        }
        final int w=i==0?canvas.w:sizes.get(i-1).w, h=i==0?canvas.h:sizes.get(i-1).h;
        final String actual=fingerprint(w,h,pixels);
        if(!actual.equals(record.get("decodedRgbaSha256").getAsString())) throw new IllegalStateException("Native field pixel identity differs at cut "+cut+" slot "+(i-1));
        unique.add(actual);visible++;
      }
      scenes++;
    }
    if(unique.size()!=report.get("uniqueVisibleImages").getAsInt() || empty!=report.get("emptySlots").getAsInt() || missing!=report.get("missingNativeTextureSlots").getAsInt()) throw new IllegalStateException("Field denominator differs");
    System.out.println("Independent native field agreement: "+scenes+" render configurations, "+visible+" visible image bindings, "+unique.size()+" unique decodes; "+empty+" empty / "+missing+" missing native slots. Visual/native acceptance pending.");
  }
}
