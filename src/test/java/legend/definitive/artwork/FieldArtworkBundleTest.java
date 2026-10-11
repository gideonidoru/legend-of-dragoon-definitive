package legend.definitive.artwork;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import static org.junit.jupiter.api.Assertions.*;
class FieldArtworkBundleTest {
  @TempDir Path temporary;
  @Test void sourceKeySelectsOnlyItsFixedResourcePartAndEntry() throws Exception {
    for(int part=0;part<4;part++) {
      final String key=Integer.toHexString(part*4)+"a".repeat(63);
      assertEquals("EnvHD-Fields-v0.1.0-part-"+part+".zip",FieldArtworkBundle.name(key));
      try(final var zip=new ZipOutputStream(Files.newOutputStream(this.temporary.resolve(FieldArtworkBundle.name(key))))) {
        zip.putNextEntry(new ZipEntry("envhd/fields/"+key+"/image-v1.png"));zip.write(new byte[]{1,2,3});zip.closeEntry();
      }
      assertArrayEquals(new byte[]{1,2,3},FieldArtworkBundle.read(this.temporary,key));
      assertThrows(IOException.class,()->FieldArtworkBundle.read(this.temporary,key.substring(0,63)+"b"));
    }
    assertThrows(IllegalArgumentException.class,()->FieldArtworkBundle.name("../other"));
  }
  @Test void oversizedInflatedEntryRejectsBeforeReading() throws Exception {
    final String key="b".repeat(64);
    try(final var zip=new ZipOutputStream(Files.newOutputStream(this.temporary.resolve(FieldArtworkBundle.name(key))))) {
      zip.putNextEntry(new ZipEntry("envhd/fields/"+key+"/image-v1.png"));
      final byte[] block=new byte[1024*1024];for(int i=0;i<33;i++) zip.write(block);zip.closeEntry();
    }
    assertThrows(IOException.class,()->FieldArtworkBundle.read(this.temporary,key));
  }
}
