package legend.definitive.rendering;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentLightTest {
  private static EnvironmentLight read(final String json) throws IOException {
    return EnvironmentLight.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
  }
  private static final String VALID="{\"version\":1,\"direction\":[0,-10,0],\"colour\":[1,0.5,0.25],\"ambient\":[0.1,0.1,0.2],\"influence\":0.35}";
  @Test void authoredDirectionNormalizesAndColourRemainsBounded() throws Exception {
    final var light=read(VALID); assertEquals(-1,light.y()); assertEquals(.35f,light.influence()); assertEquals(.25f,light.b());
    final var large=new EnvironmentLight(Float.MAX_VALUE,Float.MAX_VALUE,0,1,1,1,0,0,0,1);
    assertEquals(1,large.x()*large.x()+large.y()*large.y(),1e-6);
    assertSame(EnvironmentLight.NONE,EnvironmentLight.loadOptional(99999,99999));
  }
  @Test void malformedProfilesNeverProduceAnUncheckedLight() {
    for(final String invalid : new String[] {VALID+" {}",VALID.replace("[0,-10,0]","[0,0,0]"),VALID.replace("0.35","2"),VALID.replace("\"version\":1","\"version\":2"),VALID.replace("\"version\":1","\"version\":1.000000000001"),VALID.replace("0.35","\"0.35\"")," ".repeat(16385)}) {
      assertThrows(IOException.class,()->read(invalid));
    }
  }
}
