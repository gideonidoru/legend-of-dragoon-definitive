package legend.definitive.artwork;

import legend.game.textures.Image;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static legend.definitive.artwork.WorldTerrainAtlas.*;

/** Reviewed static crops only. Unmapped faces continue sampling native animated VRAM. */
public final class BattleMaterialAtlas implements TextureUv {
  private record Region(int page, int clut, int u, int v, int x, int y, int w, int h) { }
  private final Image image;
  private final List<Region> regions;
  public final String modelHash, timHash;
  private BattleMaterialAtlas(Image image, List<Region> regions, String modelHash, String timHash) { this.image=image; this.regions=List.copyOf(regions); this.modelHash=modelHash; this.timHash=timHash; }
  public int width() { return this.image.width; }
  public int height() { return this.image.height; }
  public ByteBuffer rgba() { return ByteBuffer.wrap(this.image.data).asReadOnlyBuffer(); }
  @Override public float[] map(int page, int clut, float u, float v) {
    for(final Region r:this.regions) if(r.page==(page & 0x19f) && r.clut==clut) {
      if(!Float.isFinite(u) || !Float.isFinite(v) || u<r.u || v<r.v || u>=r.u+r.w/4.0f || v>=r.v+r.h/4.0f) throw new IllegalArgumentException("Battle UV outside source crop");
      return new float[]{(r.x+(u-r.u)*4)/this.width(),(r.y+(v-r.v)*4)/this.height()};
    }
    return null;
  }
  public static BattleMaterialAtlas load(Class<?> owner,String base,byte[] model,byte[] tim) throws IOException {
    return read(ArtworkResources.read(owner,base+"/manifest.json",65536),ArtworkResources.read(owner,base+"/atlas-v1.png",32*1024*1024),model,tim);
  }
  public static BattleMaterialAtlas read(byte[] manifest,byte[] png,byte[] model,byte[] tim) throws IOException {
    if(manifest.length>65536 || png.length>32*1024*1024 || model.length>16*1024*1024 || tim.length>16*1024*1024) throw new IOException("Battle atlas bounds");
    final var meta=object(json(manifest));
    if(!meta.keySet().equals(Set.of("schema","pipeline","modelSha256","timSha256","scale","atlasSize","atlasSha256","regions")) || integer(meta.get("schema"))!=1 || integer(meta.get("scale"))!=4 || !"envhd-static-battle-atlas-1".equals(meta.get("pipeline"))) throw new IOException("Battle atlas contract");
    final String mh=string(meta.get("modelSha256")),th=string(meta.get("timSha256"));ArtworkResources.hash(model,mh);ArtworkResources.hash(tim,th);ArtworkResources.hash(png,string(meta.get("atlasSha256")));
    final List<Integer> size=integers(meta.get("atlasSize"));
    if(size.size()!=2 || size.get(0)!=4096 || size.get(1)<4 || size.get(1)>4096 || png.length<26 || png[25]!=6) throw new IOException("Battle atlas dimensions");
    final Image image=ArtworkResources.image(png,size.get(0),size.get(1));
    final var bytes=ByteBuffer.wrap(tim).order(ByteOrder.LITTLE_ENDIAN);
    final int bpp=bytes.getInt(4)&3,offset=8+bytes.getInt(8);
    final int ix=Short.toUnsignedInt(bytes.getShort(offset+4)),iy=Short.toUnsignedInt(bytes.getShort(offset+6)),divisor=bpp==0?4:2;
    if(bpp>1) throw new IOException("Battle indexed source required");
    final List<Region> regions=new ArrayList<>();final Set<Integer> keys=new HashSet<>();final List<int[]> padded=new ArrayList<>();
    for(final Object entry:array(meta.get("regions"))) {
      final var r=object(entry);
      if(!r.keySet().equals(Set.of("page","clut","sourceCrop","sourceUvOrigin","sourceSize","decodedRgbaSha256","atlasRect"))) throw new IOException("Battle region fields");
      final int page=integer(r.get("page")),clut=integer(r.get("clut"));
      if(!keys.add(page<<16|clut) || (page>>>7&3)!=bpp) throw new IOException("Battle region CLUT");
      final Image source=decodeSource(tim,page,clut);
      final var crop=integers(r.get("sourceCrop"));final var origin=integers(r.get("sourceUvOrigin"));final var extent=integers(r.get("sourceSize"));final var rect=integers(r.get("atlasRect"));
      if(crop.size()!=4 || origin.size()!=2 || extent.size()!=2 || rect.size()!=4) throw new IOException("Battle region dimensions");
      final int l=crop.get(0),t=crop.get(1),w=crop.get(2)-l,h=crop.get(3)-t,x=rect.get(0),y=rect.get(1);
      if(l<0 || t<0 || w<1 || h<1 || l+w>source.width || t+h>source.height || extent.get(0)!=w || extent.get(1)!=h || rect.get(2)!=w*4 || rect.get(3)!=h*4 || x<32 || y<32 || x+w*4+32>image.width || y+h*4+32>image.height || origin.get(0)!=(ix-(page&15)*64)*divisor+l || origin.get(1)!=iy-(page&16)*16+t) throw new IOException("Battle region source crop");
      final byte[] rgba=new byte[w*h*4];
      for(int v=0;v<h;v++) for(int u=0;u<w;u++) { final int p=((t+v)*source.width+l+u)*4,q=(v*w+u)*4;System.arraycopy(source.data,p,rgba,q,4); }
      if(!SkySource.fingerprint(new Image(rgba,w,h)).equals(r.get("decodedRgbaSha256"))) throw new IOException("Battle decoded source differs: "+mh+" page="+page+" clut="+clut);
      for(int v=-32;v<h*4+32;v++) for(int u=-32;u<w*4+32;u++) {
        final int q=(Math.clamp(Math.floorDiv(v,4),0,h-1)*w+Math.clamp(Math.floorDiv(u,4),0,w-1))*4,p=((y+v)*image.width+x+u)*4;
        if(rgba[q+3]!=image.data[p+3] || ((rgba[q]|rgba[q+1]|rgba[q+2])==0)!=((image.data[p]|image.data[p+1]|image.data[p+2])==0)) throw new IOException("Battle STP or black changed");
        if(u<0 || v<0 || u>=w*4 || v>=h*4) { final int inside=((y+Math.clamp(v,0,h*4-1))*image.width+x+Math.clamp(u,0,w*4-1))*4;for(int c=0;c<4;c++) if(image.data[p+c]!=image.data[inside+c]) throw new IOException("Battle padding differs"); }
      }
      final int[] area={x-32,y-32,w*4+64,h*4+64};
      // Deduplicated materials may share exactly the same padded crop.
      for(final int[] previous:padded) if(area[0]<previous[0]+previous[2] && previous[0]<area[0]+area[2] && area[1]<previous[1]+previous[3] && previous[1]<area[1]+area[3] && !java.util.Arrays.equals(area,previous)) throw new IOException("Battle regions overlap");
      padded.add(area);regions.add(new Region(page,clut,origin.get(0),origin.get(1),x,y,w*4,h*4));
    }
    if(regions.isEmpty()) throw new IOException("Empty battle selection");
    for(int y=0;y<image.height;y++) { final boolean[] occupied=new boolean[image.width];for(final int[] r:padded) if(y>=r[1] && y<r[1]+r[3]) java.util.Arrays.fill(occupied,r[0],r[0]+r[2],true);for(int x=0;x<image.width;x++) if(!occupied[x]) { final int p=(y*image.width+x)*4;if((image.data[p]|image.data[p+1]|image.data[p+2]|image.data[p+3])!=0) throw new IOException("Battle atlas gap content"); } }
    return new BattleMaterialAtlas(image,regions,mh,th);
  }
  /** Reconstruct the actual upload: CLUT words can overwrite overlapping image words. */
  static Image decodeSource(final byte[] tim, final int page, final int clut) throws IOException {
    if(tim.length<20 || tim.length>16*1024*1024) throw new IOException("Battle TIM bounds");
    try {
      final ByteBuffer bytes=ByteBuffer.wrap(tim).order(ByteOrder.LITTLE_ENDIAN);
      final int flags=bytes.getInt(4),bpp=page>>>7&3;
      if(bytes.getInt(0)!=16 || flags!=8 && flags!=9 || (flags&3)!=bpp || clut<0 || clut>65535) throw new IOException("Battle TIM format");
      final var palette=WorldTerrainSource.block(bytes,8);
      final int offset=8+bytes.getInt(8);
      final var rect=WorldTerrainSource.block(bytes,offset);
      if(offset+12+rect.words().length*2!=tim.length) throw new IOException("Trailing battle TIM content");
      final short[] vram=new short[1024*512];final boolean[] written=new boolean[vram.length];
      WorldTerrainSource.upload(rect,vram,written);WorldTerrainSource.upload(palette,vram,written);
      final int divisor=bpp==0?4:2,bits=bpp==0?4:8,colours=bpp==0?16:256,cx=(clut&63)*16,cy=clut>>>6;
      if(cy>=512 || cx+colours>1024) throw new IOException("Battle palette bounds");
      for(int c=0;c<colours;c++) if(!written[cy*1024+cx+c]) throw new IOException("Uninitialized battle palette");
      final byte[] rgba=new byte[rect.w()*divisor*rect.h()*4];
      for(int y=0;y<rect.h();y++) for(int x=0;x<rect.w()*divisor;x++) {
        final int index=Short.toUnsignedInt(vram[(rect.y()+y)*1024+rect.x()+x/divisor])>>>(x%divisor*bits)&((1<<bits)-1);
        final int colour=Short.toUnsignedInt(vram[cy*1024+cx+index]),p=(y*rect.w()*divisor+x)*4;
        rgba[p]=(byte)((colour&31)*255/31);rgba[p+1]=(byte)((colour>>>5&31)*255/31);rgba[p+2]=(byte)((colour>>>10&31)*255/31);rgba[p+3]=(byte)((colour>>>15)*255);
      }
      return new Image(rgba,rect.w()*divisor,rect.h());
    } catch(final IndexOutOfBoundsException failure) { throw new IOException("Malformed battle TIM",failure); }
  }

}
