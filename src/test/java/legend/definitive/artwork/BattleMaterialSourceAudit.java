package legend.definitive.artwork;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
/** Actual reviewed atlas decoding and every original polygon UV; no renderer/window. */
public final class BattleMaterialSourceAudit {
  public static void main(String[] args) throws Exception {
    final Path files=Path.of(args[0]),runtime=Path.of(args[1]);
    final var inventory=JsonParser.parseString(Files.readString(Path.of(args[2]))).getAsJsonObject();int faces=0,stages=0;
    for(final var item:inventory.getAsJsonArray("scenes")) {
      final var s=item.getAsJsonObject();final int stage=s.get("stage").getAsInt();final Path source=files.resolve("SECT/DRGN0.BIN/"+(2497+stage));
      final byte[] model=Files.readAllBytes(source.resolve("0/0")),tim=Files.readAllBytes(source.resolve("2"));final Path folder=runtime.resolve("battle-materials/"+s.get("modelSha256").getAsString());
      final var atlas=BattleMaterialAtlas.read(Files.readAllBytes(folder.resolve("manifest.json")),Files.readAllBytes(folder.resolve("atlas-v1.png")),model,tim);
      final var bytes=ByteBuffer.wrap(model).order(ByteOrder.LITTLE_ENDIAN);final int base=bytes.getInt(0)+12,parts=bytes.getInt(base-4);
      for(int p=0;p<parts;p++) { final int entry=base+p*28,count=bytes.getInt(entry+20);int offset=base+bytes.getInt(entry+16);
        for(int f=0;f<count;f++) { final int header=bytes.getInt(offset),mode=header>>>24,size=(header>>>8&255)*4;
          if((mode&4)!=0) { final int clut=Short.toUnsignedInt(bytes.getShort(offset+6)),page=Short.toUnsignedInt(bytes.getShort(offset+10));for(int v=0;v<((mode&8)!=0?4:3);v++) atlas.map(page,clut,Byte.toUnsignedInt(model[offset+4+v*4]),Byte.toUnsignedInt(model[offset+5+v*4]));faces++; }
          offset+=4+size;
        }
      } stages++;
    }
    System.out.println("Actual static battle atlases verified: "+stages+" stages, "+faces+" original textured faces; source pixels/CLUT/STP/black/padding and UV bounds checked. Gameplay/final visual acceptance pending.");
  }
}
