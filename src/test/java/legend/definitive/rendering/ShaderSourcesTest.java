package legend.definitive.rendering;

import legend.core.renderer.ShaderSources;
import legend.core.renderer.ShaderManager;
import legend.core.renderer.ShaderStage;
import legend.core.memory.types.IntRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class ShaderSourcesTest {
  @TempDir Path temp;
  @Test void includesRemainLocalBoundedAndAcyclic() throws Exception {
    final Path root=Files.createDirectory(this.temp.resolve("shaders")), main=root.resolve("main.fsh"), helper=root.resolve("helper.glsl");
    Files.writeString(helper,"vec3 helper;\n"); Files.writeString(main,"#version 330 core\n#include \"helper.glsl\"\n");
    assertTrue(ShaderSources.read(main).contains("vec3 helper"));
    Files.writeString(helper,"#include \"main.fsh\"\n"); assertThrows(IOException.class,()->ShaderSources.read(main));
    Files.writeString(this.temp.resolve("outside"),"data"); Files.writeString(main,"#include \"../outside\"\n"); assertThrows(IOException.class,()->ShaderSources.read(main));
    Files.writeString(main," ".repeat(1_048_577)); assertThrows(IOException.class,()->ShaderSources.read(main));
  }
  @Test void shippingSmaaAndModelShadersTranspileToGles() throws Exception {
    for(final String name : new String[] {"post.vsh","smaa_edge.fsh","smaa_weights.fsh","smaa_blend.fsh","tmd.vsh","battle_tmd.vsh","tmd.gsh","tmd.fsh","battle_tmd.fsh"}) {
      final ShaderStage stage=name.endsWith("vsh") ? ShaderStage.VERTEX : name.endsWith("gsh") ? ShaderStage.GEOMETRY : ShaderStage.FRAGMENT;
      assertTrue(ShaderManager.transpileShader(ShaderSources.read(Path.of("gfx/shaders",name)),stage,new IntRef()).contains("#version 320 es"),name);
    }
  }
}
