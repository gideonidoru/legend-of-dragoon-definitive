// Original synthetic GPU probe (2026-10-10), AGPL v3; see LICENSE. Test tooling only.
package legend.definitive.materials;

import com.google.gson.GsonBuilder;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.sdl.SDLError.SDL_GetError;
import static org.lwjgl.sdl.SDLHints.SDL_HINT_MAC_BACKGROUND_APP;
import static org.lwjgl.sdl.SDLHints.SDL_SetHint;
import static org.lwjgl.sdl.SDLInit.*;
import static org.lwjgl.sdl.SDLVideo.*;

/** Actual checked-out battle shaders on a hidden context; no game, queue or renderer lifecycle claim. */
public final class NativeBattleShaderProbe {
  static final int WIDTH = 128, HEIGHT = 64;
  static final int[][] PALETTES = {{0, 0x8000, 0x801f, 7 | 15 << 5 | 27 << 10},
    {0, 0x8000, 0x83e0, 27 | 10 << 5 | 7 << 10}};
  private NativeBattleShaderProbe() { }

  enum Sample {
    OPAQUE_UNLIT(6, 0), OPAQUE_LIT(7, 0), TRANSLUCENT_COMBINED(14, 0),
    TRANSLUCENT_OPAQUE_PASS(14, 1), TRANSLUCENT_STP_PASS(14, 2),
    OPAQUE_IN_STP_PASS(6, 2), UNTEXTURED_UNLIT(4, 0), UNTEXTURED_LIT(5, 0);
    final int flags, discard;
    Sample(final int flags, final int discard) { this.flags = flags; this.discard = discard; }
    String label() { return this.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'); }
  }

  static int pixel(final int palette, final int x, final int y) { return PALETTES[palette][(x + 2 * y) % 4]; }
  static boolean visible(final int pixel, final Sample sample) {
    final boolean textured = (sample.flags & 2) != 0, translucent = (sample.flags & 8) != 0;
    if(textured && pixel == 0) return false;
    if(sample.discard == 1 && translucent && (!textured || (pixel & 0x8000) != 0)) return false;
    return sample.discard != 2 || translucent && (!textured || (pixel & 0x8000) != 0);
  }
  static byte[] expected(final Sample sample) {
    final byte[] data = new byte[WIDTH * HEIGHT * 4];
    for(int y = 0; y < HEIGHT; y++) for(int x = 0; x < WIDTH; x++) {
      final int value = pixel(x / (WIDTH / 2), x % (WIDTH / 2) / 16, y / 16);
      if(!visible(value, sample)) continue;
      final int offset = (y * WIDTH + x) * 4;
      for(int channel = 0; channel < 3; channel++) {
        final float colour = (sample.flags & 2) == 0 ? 1 : ((value >>> (channel * 5)) & 31) / 31.0f;
        final float light = (sample.flags & 1) == 0 ? 1 : new float[]{.85f, .8f, .75f}[channel];
        data[offset + channel] = (byte)Math.round(Math.min(1, colour * light) * 255);
      }
      data[offset + 3] = (byte)((sample.flags & 8) == 0 ? 255 : 128);
    }
    return data;
  }
  /** Independent palette oracle: same native visibility; two different subtexel mixtures. */
  static byte[] expectedFx(final Sample sample) {
    final byte[] data = expected(sample);
    if((sample.flags & 2) == 0) return data;
    for(int y = 0; y < HEIGHT; y++) for(int x = 0; x < WIDTH; x++) {
      final int palette = x / (WIDTH / 2), index = (x % (WIDTH / 2) / 16 + 2 * (y / 16)) % 4;
      final int value = PALETTES[palette][index];
      if(!visible(value, sample) || (value & 0x7fff) == 0) continue;
      final int other = PALETTES[palette][index == 2 ? 3 : 2];
      final float fraction = (x % 16 < 8 ? 63 : 127) / 254.0f;
      for(int channel = 0; channel < 3; channel++) {
        final float a = ((value >>> (channel * 5)) & 31) / 31.0f, b = ((other >>> (channel * 5)) & 31) / 31.0f;
        final float light = (sample.flags & 1) == 0 ? 1 : new float[]{.85f,.8f,.75f}[channel];
        data[(y * WIDTH + x) * 4 + channel] = (byte)Math.round((a + (b - a) * fraction) * light * 255);
      }
    }
    return data;
  }
  record Difference(int coverageMismatches, int maximumChannelError, int visiblePixels, int visibleBlackPixels) { }
  static Difference compare(final byte[] expected, final byte[] actual) {
    if(expected.length != WIDTH * HEIGHT * 4 || actual.length != expected.length) throw new IllegalArgumentException("Expected bounded RGBA frames");
    int coverage = 0, maximum = 0, visible = 0, black = 0;
    for(int offset = 0; offset < expected.length; offset += 4) {
      if((expected[offset + 3] != 0) != (actual[offset + 3] != 0)) coverage++;
      if(actual[offset + 3] != 0) {
        visible++;
        if(actual[offset] == 0 && actual[offset + 1] == 0 && actual[offset + 2] == 0) black++;
      }
      for(int channel = 0; channel < 4; channel++) maximum = Math.max(maximum, Math.abs(Byte.toUnsignedInt(expected[offset + channel]) - Byte.toUnsignedInt(actual[offset + channel])));
    }
    return new Difference(coverage, maximum, visible, black);
  }

  private static String shader(final Path path) throws IOException {
    final byte[] bytes;
    try(final var stream = Files.newInputStream(path)) { bytes = stream.readNBytes(128 * 1024 + 1); }
    if(bytes.length == 0 || bytes.length > 128 * 1024) throw new IOException("Shader exceeds the probe budget");
    return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
  }
  private static int compile(final int kind, final String source) {
    final int id = glCreateShader(kind);
    try {
      glShaderSource(id, source); glCompileShader(id);
      if(glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) throw new IllegalStateException("Shader compilation failed: " + glGetShaderInfoLog(id, 8192));
      return id;
    } catch(final RuntimeException error) { glDeleteShader(id); throw error; }
  }
  private static void check(final String operation) {
    final int error = glGetError();
    if(error != GL_NO_ERROR) throw new IllegalStateException(operation + " produced OpenGL error " + error);
  }
  private static void png(final Path path, final byte[] rgba) throws IOException {
    final var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
    for(int y = 0; y < HEIGHT; y++) for(int x = 0; x < WIDTH; x++) {
      final int i = (y * WIDTH + x) * 4;
      image.setRGB(x, y, Byte.toUnsignedInt(rgba[i + 3]) << 24 | Byte.toUnsignedInt(rgba[i]) << 16 | Byte.toUnsignedInt(rgba[i + 1]) << 8 | Byte.toUnsignedInt(rgba[i + 2]));
    }
    if(!ImageIO.write(image, "png", path.toFile())) throw new IOException("No PNG writer available");
  }

  static final class Context implements AutoCloseable {
    long window, context;
    boolean initialised;
    int program, vao, vertices, framebuffer, colour, tex15, tex24, detail;
    final List<Integer> buffers = new ArrayList<>();
    final Map<String, Integer> blockBytes = new LinkedHashMap<>();
    Context(final Map<String, String> shaders) {
      try {
        final boolean mac = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("mac");
        if(mac && !SDL_SetHint(SDL_HINT_MAC_BACKGROUND_APP, "1")) throw new IllegalStateException("Could not request background-only SDL startup");
        if(!SDL_Init(SDL_INIT_VIDEO)) throw new IllegalStateException("SDL video initialization failed: " + SDL_GetError());
        this.initialised = true;
        attribute(SDL_GL_CONTEXT_MAJOR_VERSION, mac ? 4 : 3); attribute(SDL_GL_CONTEXT_MINOR_VERSION, mac ? 1 : 3);
        attribute(SDL_GL_CONTEXT_PROFILE_MASK, SDL_GL_CONTEXT_PROFILE_CORE);
        attribute(SDL_GL_CONTEXT_FLAGS, SDL_GL_CONTEXT_FORWARD_COMPATIBLE_FLAG);
        this.window = SDL_CreateWindow("Definitive hidden shader probe", WIDTH, HEIGHT, SDL_WINDOW_OPENGL | SDL_WINDOW_HIDDEN);
        if(this.window == 0) throw new IllegalStateException("Hidden window creation failed: " + SDL_GetError());
        this.context = SDL_GL_CreateContext(this.window);
        if(this.context == 0 || !SDL_GL_MakeCurrent(this.window, this.context)) throw new IllegalStateException("Hidden context creation failed: " + SDL_GetError());
        if(!GL.createCapabilities().OpenGL33) throw new IllegalStateException("OpenGL 3.3 is required by the checked shaders");
        this.program = glCreateProgram();
        final List<Integer> compiled = new ArrayList<>();
        try {
          for(final var stage : List.of(Map.entry(GL_VERTEX_SHADER, "battle_tmd.vsh"), Map.entry(GL_GEOMETRY_SHADER, "tmd.gsh"), Map.entry(GL_FRAGMENT_SHADER, "battle_tmd.fsh"))) {
            final int id = compile(stage.getKey(), shaders.get(stage.getValue())); compiled.add(id); glAttachShader(this.program, id);
          }
          glLinkProgram(this.program);
          if(glGetProgrami(this.program, GL_LINK_STATUS) == GL_FALSE) throw new IllegalStateException("Shader linking failed: " + glGetProgramInfoLog(this.program, 8192));
        } finally { for(final int id : compiled) { glDetachShader(this.program, id); glDeleteShader(id); } }
        glUseProgram(this.program);
        glUniform1i(glGetUniformLocation(this.program, "tex24"), 0); glUniform1i(glGetUniformLocation(this.program, "tex15"), 1);
        glUniform1i(glGetUniformLocation(this.program, "effectDetailTex"), 6);
        glUniform3f(glGetUniformLocation(this.program, "recolour"), 1, 1, 1);
        glUniform1f(glGetUniformLocation(this.program, "modelIndex"), 0);
        this.blocks(); this.textures(); this.detail(false); this.target();
        this.vao = glGenVertexArrays(); glBindVertexArray(this.vao);
        this.vertices = glGenBuffers(); glBindBuffer(GL_ARRAY_BUFFER, this.vertices);
        final int[] sizes = {4, 3, 2, 1, 1, 4, 1}; int offset = 0;
        for(int i = 0; i < sizes.length; i++) { glVertexAttribPointer(i, sizes[i], GL_FLOAT, false, 16 * Float.BYTES, (long)offset * Float.BYTES); glEnableVertexAttribArray(i); offset += sizes[i]; }
        glDisable(GL_BLEND); glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE); glDisable(GL_DITHER); glDisable(GL_FRAMEBUFFER_SRGB);
        glViewport(0, 0, WIDTH, HEIGHT); check("Probe initialization");
      } catch(final RuntimeException | Error error) { this.close(); throw error; }
    }
    private static void attribute(final int name, final int value) { if(!SDL_GL_SetAttribute(name, value)) throw new IllegalStateException("SDL GL attribute failed: " + SDL_GetError()); }
    private void block(final String name, final int binding, final float[] values) {
      final int index = glGetUniformBlockIndex(this.program, name);
      if(index == GL_INVALID_INDEX) throw new IllegalStateException("Required shader block missing: " + name);
      final int size = glGetActiveUniformBlocki(this.program, index, GL_UNIFORM_BLOCK_DATA_SIZE);
      if(size > values.length * Float.BYTES) throw new IllegalStateException("Shader block budget mismatch: " + name);
      final int buffer = glGenBuffers(); this.buffers.add(buffer); this.blockBytes.put(name, size);
      glBindBuffer(GL_UNIFORM_BUFFER, buffer); glBufferData(GL_UNIFORM_BUFFER, values, GL_STATIC_DRAW);
      glBindBufferBase(GL_UNIFORM_BUFFER, binding, buffer); glUniformBlockBinding(this.program, index, binding);
    }
    private void blocks() {
      final float[] transforms = new float[32]; identity(transforms, 0); identity(transforms, 16); this.block("transforms", 0, transforms);
      final float[] models = new float[128 * 20]; identity(models, 0); this.block("transforms2", 1, models);
      final float[] lighting = new float[128 * 32]; lighting[8] = lighting[9] = lighting[10] = lighting[15] = 1;
      lighting[16] = .7f; lighting[21] = .6f; lighting[26] = .5f; lighting[28] = .15f; lighting[29] = .2f; lighting[30] = .25f;
      this.block("lighting", 2, lighting);
      final float[] cluts = new float[1024 * 4]; cluts[0] = -1; this.block("clutAnimation", 3, cluts);
      this.block("projectionInfo", 4, new float[]{0, .1f, 10, 0}); this.block("scissor", 5, new float[]{0, 0, WIDTH, HEIGHT});
    }
    private static void identity(final float[] data, final int offset) { for(int i = 0; i < 4; i++) data[offset + i * 5] = 1; }
    private static void parameters() { glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE); }
    private void textures() {
      this.tex15 = glGenTextures(); glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, this.tex15); parameters();
      final var vram = MemoryUtil.memCallocShort(1024 * 512);
      try {
        for(int y = 0; y < 4; y++) { int word = 0; for(int x = 0; x < 4; x++) word |= ((x + 2 * y) % 4) << (x * 4); vram.put(y * 1024, (short)word); }
        for(int palette = 0; palette < 2; palette++) for(int i = 0; i < 4; i++) vram.put((256 + palette) * 1024 + i, (short)PALETTES[palette][i]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R16UI, 1024, 512, 0, GL_RED_INTEGER, GL_UNSIGNED_SHORT, vram);
      } finally { MemoryUtil.memFree(vram); }
      this.tex24 = glGenTextures(); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, this.tex24); parameters();
      final ByteBuffer rgba = MemoryUtil.memAlloc(8 * 4 * 4);
      try {
        for(int y = 0; y < 4; y++) for(int x = 0; x < 8; x++) {
          final int value = pixel(x / 4, x % 4, y);
          for(int channel = 0; channel < 3; channel++) rgba.put((byte)(((value >>> (channel * 5)) & 31) * 255 / 31));
          rgba.put((byte)((value & 0x8000) == 0 ? 0 : 255));
        }
        rgba.flip(); glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 8, 4, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
      } finally { MemoryUtil.memFree(rgba); }
    }
    private void detail(final boolean stale) {
      if(this.detail == 0) this.detail = glGenTextures();
      glActiveTexture(GL_TEXTURE6); glBindTexture(GL_TEXTURE_2D, this.detail); parameters();
      final int[] values = new int[4 * 8];
      for(int y = 0; y < 8; y++) for(int x = 0; x < 4; x++) {
        final int index = (x + 2 * (y / 2)) % 4, other = index == 2 ? 3 : 2;
        final int base = 0x8000 | (stale ? 15 : index) | other << 4;
        values[y * 4 + x] = base | 63 << 8 | (base | 127 << 8) << 16;
      }
      glTexImage2D(GL_TEXTURE_2D, 0, GL_R32UI, 4, 8, 0, GL_RED_INTEGER, GL_UNSIGNED_INT, values);
      glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, this.tex24);
    }
    private void target() {
      this.framebuffer = glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER, this.framebuffer);
      this.colour = glGenTextures(); glBindTexture(GL_TEXTURE_2D, this.colour); parameters();
      glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, WIDTH, HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer)null);
      glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.colour, 0); glDrawBuffer(GL_COLOR_ATTACHMENT0); glReadBuffer(GL_COLOR_ATTACHMENT0);
      if(glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Incomplete probe framebuffer");
      glBindTexture(GL_TEXTURE_2D, this.tex24);
    }
    byte[] render(final Sample sample, final boolean rgba) { return this.render(sample, rgba, false, false); }
    byte[] render(final Sample sample, final boolean rgba, final boolean fx, final boolean ui) {
      glUniform1i(glGetUniformLocation(this.program, "effectArtworkEnabled"), fx ? 1 : 0);
      glUniform1i(glGetUniformLocation(this.program, "uiLayer"), ui ? 1 : 0);
      final List<Float> values = new ArrayList<>();
      for(int palette = 0; palette < 2; palette++) {
        final float left = -1 + palette, right = palette;
        final float[][] corners = {{left, 1, 0, 0}, {left, -1, 0, 4}, {right, 1, 4, 0}, {right, -1, 4, 4}};
        for(final int[] triangle : new int[][]{{0,1,2}, {1,3,2}}) for(final int corner : new int[]{triangle[0],triangle[0],triangle[1],triangle[1],triangle[2],triangle[2]}) {
          final float[] point = corners[corner];
          for(final float value : new float[]{point[0],point[1],.5f,0, 0,0,1,
            rgba ? (point[2] + palette * 4) / 8 : point[2], rgba ? point[3] / 4 : point[3],
            rgba ? 3 << 7 : 0, (256 + palette) << 6, 1,1,1,1, sample.flags}) values.add(value);
        }
      }
      final float[] data = new float[values.size()]; for(int i = 0; i < data.length; i++) data[i] = values.get(i);
      glBindBuffer(GL_ARRAY_BUFFER, this.vertices); glBufferData(GL_ARRAY_BUFFER, data, GL_STREAM_DRAW);
      glUniform1f(glGetUniformLocation(this.program, "discardTranslucency"), sample.discard);
      glClearColor(0, 0, 0, 0); glClear(GL_COLOR_BUFFER_BIT); glDrawArrays(GL_TRIANGLES_ADJACENCY, 0, data.length / 16); check("Shader draw");
      final ByteBuffer pixels = MemoryUtil.memAlloc(WIDTH * HEIGHT * 4);
      try {
        glPixelStorei(GL_PACK_ALIGNMENT, 1); glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels); check("Framebuffer readback");
        final byte[] result = new byte[pixels.remaining()];
        for(int y = 0; y < HEIGHT; y++) for(int x = 0; x < WIDTH * 4; x++) result[y * WIDTH * 4 + x] = pixels.get((HEIGHT - y - 1) * WIDTH * 4 + x);
        return result;
      } finally { MemoryUtil.memFree(pixels); }
    }
    @Override public void close() {
      if(this.context != 0) {
        if(this.vertices != 0) glDeleteBuffers(this.vertices); if(this.vao != 0) glDeleteVertexArrays(this.vao);
        for(final int buffer : this.buffers) glDeleteBuffers(buffer);
        if(this.framebuffer != 0) glDeleteFramebuffers(this.framebuffer);
        for(final int texture : new int[]{this.tex15,this.tex24,this.colour,this.detail}) if(texture != 0) glDeleteTextures(texture);
        if(this.program != 0) glDeleteProgram(this.program);
        GL.setCapabilities(null); SDL_GL_DestroyContext(this.context); this.context = 0;
      }
      if(this.window != 0) { SDL_DestroyWindow(this.window); this.window = 0; }
      if(this.initialised) { SDL_QuitSubSystem(SDL_INIT_VIDEO); this.initialised = false; }
    }
  }

  public static void main(final String[] args) throws Exception {
    if(args.length != 2) throw new IllegalArgumentException("Supply the checkout root and a new synthetic report folder");
    final Path root = Path.of(args[0]).toRealPath(), output = Path.of(args[1]).toAbsolutePath().normalize();
    final Map<String, String> sources = new LinkedHashMap<>(), hashes = new LinkedHashMap<>();
    for(final String name : List.of("battle_tmd.vsh","tmd.gsh","battle_tmd.fsh")) {
      final String source = shader(root.resolve("gfx/shaders").resolve(name)); sources.put(name, source);
      hashes.put(name, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }
    Files.createDirectory(output);
    final Map<String, Object> report = new LinkedHashMap<>();
    report.put("scope", "Original synthetic shader fixtures in hidden SDL/OpenGL context; no game, QueuedModel, renderer lifecycle, asset import or Deck acceptance");
    report.put("shaderSha256", hashes); report.put("frameSize", List.of(WIDTH,HEIGHT)); report.put("hiddenWindow", true);
    final List<Map<String,Object>> cases = new ArrayList<>(); report.put("cases", cases);
    try {
      try(final var context = new Context(sources)) {
        report.put("vendor", glGetString(GL_VENDOR)); report.put("renderer", glGetString(GL_RENDERER)); report.put("version", glGetString(GL_VERSION)); report.put("uniformBlockBytes", context.blockBytes);
        for(final Sample sample : Sample.values()) {
          final byte[] expected = expected(sample), indexed = context.render(sample, false), rgba = context.render(sample, true);
          final Difference control = compare(expected, indexed), candidate = compare(expected, rgba), between = compare(indexed, rgba);
          png(output.resolve(sample.label() + "-indexed.png"), indexed); png(output.resolve(sample.label() + "-rgba.png"), rgba);
          cases.add(Map.of("sample",sample.label(),"indexedReference",control,"rgbaReference",candidate,"indexedToRgba",between));
          if(control.coverageMismatches() != 0 || candidate.coverageMismatches() != 0 || control.maximumChannelError() > 1 || candidate.maximumChannelError() > 1 || between.maximumChannelError() > 1) throw new IllegalStateException("Shader fixture failed: " + sample.label() + " " + control + " " + candidate + " " + between);
        }
        for(final Sample sample : Sample.values()) {
          final byte[] frame = context.render(sample, false, true, false);
          final Difference result = compare(expectedFx(sample), frame);
          png(output.resolve(sample.label() + "-fx.png"), frame);
          cases.add(Map.of("sample",sample.label() + "-live-palette-fx","result",result));
          if(result.coverageMismatches() != 0 || result.maximumChannelError() > 1) throw new IllegalStateException("FX palette/mask fixture failed: " + sample.label() + " " + result);
        }
        final Sample opaque = Sample.OPAQUE_UNLIT;
        final Difference protectedUi = compare(expected(opaque), context.render(opaque, false, true, true));
        context.detail(true);
        final Difference stale = compare(expected(opaque), context.render(opaque, false, true, false));
        cases.add(Map.of("sample","fx-protected-ui","result",protectedUi)); cases.add(Map.of("sample","fx-stale-controls","result",stale));
        if(protectedUi.maximumChannelError() > 1 || stale.maximumChannelError() > 1) throw new IllegalStateException("FX fallback/protected UI fixture failed");
        check("Final GPU check");
      }
      report.put("result", "passed"); report.put("probeCleanupCompleted", true);
    } catch(final Exception | Error error) { report.put("result", "failed"); report.put("error", error.toString()); throw error; }
    finally { Files.writeString(output.resolve("shader-report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
    System.out.println("Checked " + cases.size() + " original native-shader cases in a hidden context; report: " + output);
  }
}
