// Custom resource integrity checks (2026-10-10), AGPL v3.
package legend.definitive.effects;

import fxhd.FxHdMod;
import legend.definitive.textures.TexturePilot;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class FxHdResourcesTest {
  static final String[] NAMES = {"dust", "smoke_1", "smoke_2_dust_palette", "left_foot", "right_foot", "savepoint_big_circle"};

  @Test void allSixSelectedResourcesAreBoundedHashedAndUseBinaryStp() throws Exception {
    long bytes = 0;
    for(final String name : NAMES) {
      final Properties properties = new Properties();
      try(final var in = FxHdMod.class.getResourceAsStream("/fxhd/" + name + ".properties")) {
        assertNotNull(in, name); properties.load(in);
      }
      final byte[] png;
      try(final var in = FxHdMod.class.getResourceAsStream("/fxhd/" + name + ".png")) { assertNotNull(in, name); png = in.readAllBytes(); }
      assertEquals(properties.getProperty("outputSha256"), TexturePilot.sha256(png), name);
      assertTrue(properties.getProperty("sourceSha256").matches("[a-f0-9]{64}")); assertEquals("2", properties.getProperty("scale"));
      final var image = ImageIO.read(new java.io.ByteArrayInputStream(png));
      assertEquals(name.contains("foot") ? 32 : 64, image.getWidth()); assertEquals(64, image.getHeight());
      for(int y = 0; y < image.getHeight(); y++) for(int x = 0; x < image.getWidth(); x++) {
        final int alpha = image.getRGB(x, y) >>> 24;
        assertTrue(alpha == 0 || alpha == 255, name);
      }
      bytes += image.getWidth() * image.getHeight() * 4L;
    }
    assertEquals(81920, bytes); assertTrue(bytes < 16 * 1024 * 1024);
    assertNull(FxHdMod.class.getResourceAsStream("/fxhd/smoke_2.png"));
    assertNull(FxHdMod.class.getResourceAsStream("/fxhd/savepoint.png"));
  }
}
