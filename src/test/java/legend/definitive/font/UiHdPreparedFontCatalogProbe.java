package legend.definitive.font;

import legend.core.GameEngine;
import legend.core.gpu.Bpp;
import legend.core.gte.MV;
import legend.core.renderer.*;
import legend.core.renderer.noop.NoopApi;
import legend.game.modding.coremod.CoreMod;
import legend.game.saves.ConfigRegistryEvent;
import legend.game.textures.*;
import legend.game.tim.Tim;
import legend.game.unpacker.FileData;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Explicit private-source probe; never runs the game or publishes original pixels. */
public final class UiHdPreparedFontCatalogProbe {
  private UiHdPreparedFontCatalogProbe() { }
  record Source(NativeUiTextureEvent event, byte[] imageInput, byte[] paletteInput) { }
  record Glyph(JSONObject entry, Source source, NativeUiQuad quad, Image indices, Image artwork) { }
  static void require(final boolean condition, final String message) {
    if(!condition) throw new AssertionError(message);
  }
  static String hash(final byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }
  static Object field(final Object owner, final Class<?> type, final String name) throws Exception {
    final var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner);
  }
  static void reset() throws Exception {
    final Method reset = RenderEngine.class.getDeclaredMethod("resetBatches"); reset.setAccessible(true); reset.invoke(GameEngine.RENDERER);
    NativeSpriteFont.beginFrame();
  }
  static Image sourceIndices(final Tim tim, final int u, final int v, final int w, final int h) {
    final var rect = tim.getImageRect(); final byte[] data = tim.getImageData().getBytes();
    require(tim.getBpp() == Bpp.BITS_4 && u >= 0 && v >= 0 && w > 0 && h > 0
      && (long)u + w <= rect.w * 4L && (long)v + h <= rect.h, "Source crop exceeds actual indexed page");
    final byte[] rgba = new byte[Math.multiplyExact(Math.multiplyExact(w, h), 4)];
    // Read native little-endian words independently of the runtime loader's byte-nibble extraction.
    final var words = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
    for(int y = 0; y < h; y++) for(int x = 0; x < w; x++) {
      final int word = words.get((v + y) * rect.w + (u + x) / 4) & 65535;
      final int p = (y * w + x) * 4;
      rgba[p] = (byte)(word >>> ((u + x) % 4 * 4) & 15); rgba[p + 3] = (byte)255;
    }
    return new Image(rgba, w, h);
  }
  static Image png(final byte[] bytes) throws Exception {
    require(bytes.length >= 33 && bytes[24] == 8 && bytes[25] == 6, "Actual PNG must be RGBA8");
    final var image = ImageIO.read(new ByteArrayInputStream(bytes)); require(image != null, "PNG cannot decode");
    final byte[] pixels = new byte[image.getWidth() * image.getHeight() * 4]; boolean covered = false;
    for(int y = 0; y < image.getHeight(); y++) for(int x = 0; x < image.getWidth(); x++) {
      final int argb = image.getRGB(x, y), p = (y * image.getWidth() + x) * 4;
      final int index = argb >>> 16 & 255, alpha = argb >>> 24;
      require(index <= 15 && (argb & 65535) == 0, "Artwork must encode palette indices, not baked RGB");
      pixels[p] = (byte)index; pixels[p + 3] = (byte)alpha; covered |= alpha != 0;
    }
    require(covered, "Artwork has no coverage"); return new Image(pixels, image.getWidth(), image.getHeight());
  }
  static NativeUiQuad quad(final NativeUiTextureEvent event, final JSONObject entry) {
    final var crop = entry.getJSONArray("crop"); final int u = crop.getInt(0), v = crop.getInt(1), palette = entry.getInt("palette");
    // Physical word/page addressing, rather than a guessed packed CLUT constant.
    final int wordX = event.imageX + u / 4, row = event.imageY + v;
    final int pageX = wordX & ~63, pageY = row & ~255;
    return new NativeUiQuad(pageX, pageY, event.clutX + 16 * (palette / event.clutRows), event.clutY + palette % event.clutRows,
      u + 4 * (event.imageX - pageX), row - pageY, crop.getInt(2), crop.getInt(3));
  }
  private static byte[] bounded(final Path path, final int limit) throws Exception {
    require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "Missing or linked input: " + path);
    try(final var input = Files.newInputStream(path)) {
      final byte[] bytes = input.readNBytes(limit + 1); require(bytes.length <= limit, "Input exceeds bound: " + path); return bytes;
    }
  }
  static Map<String, Source> sources(final Path files) throws Exception {
    final Path directory = files.resolve("SECT/DRGN0.BIN"); final var result = new LinkedHashMap<String, Source>();
    final byte[] image = bounded(directory.resolve("4113/0"), 1024 * 1024);
    for(int bank = 0; bank < 6; bank++) {
      final byte[] palette = bounded(directory.resolve("4113/" + bank), 1024 * 1024);
      final var paletteTim = new Tim(new FileData(palette));
      // Battle.combatUiTexturesLoaded relocates these palettes; embedded TIM rects are not the uploads.
      final int clutX = bank < 4 ? 704 + bank * 16 : 896 + (bank - 4) * 16, clutY = bank < 4 ? 496 : 304;
      final var event = new NativeUiTextureEvent("battle_hud_" + bank,
        NativeUiTextureEvent.withPalette(new Tim(new FileData(image)), paletteTim), 704, 256, clutX, clutY);
      result.put(event.id, new Source(event, image, palette));
    }
    final byte[] menu = bounded(directory.resolve("6665"), 1024 * 1024); final Tim menuTim = new Tim(new FileData(menu));
    final var imageRect = menuTim.getImageRect(); final var paletteRect = menuTim.getClutRect();
    final var main = new NativeUiTextureEvent("menu", menuTim, imageRect.x - 512, imageRect.y, paletteRect.x - 512, paletteRect.y);
    result.put(main.id, new Source(main, menu, menu));
    final byte[] first = new Tim(new FileData(menu).slice(0x10460)).getClutData().getBytes();
    final byte[] second = new Tim(new FileData(menu).slice(0x10580)).getClutData().getBytes();
    require(first.length >= 4 * 32 && second.length >= 9 * 32, "Menu secondary palette blocks incomplete");
    final byte[] extra = new byte[13 * 32]; System.arraycopy(first, 0, extra, 0, 4 * 32); System.arraycopy(second, 0, extra, 4 * 32, 9 * 32);
    final var secondary = new NativeUiTextureEvent("menu_numeric_extras", NativeUiTextureEvent.withPaletteData(menuTim, extra), 64, 256, 176, 496);
    result.put(secondary.id, new Source(secondary, menu, extra)); return result;
  }
  private static byte[] resource(final Class<?> provider, final String name) throws Exception {
    try(final var input = provider.getResourceAsStream(name)) {
      require(input != null, "Missing packaged resource: " + name);
      final byte[] bytes = input.readNBytes(4 * 1024 * 1024 + 1); require(bytes.length <= 4 * 1024 * 1024, "Resource exceeds bound"); return bytes;
    }
  }
  static List<Glyph> catalog(final Class<?> provider, final Map<String, Source> sources, final byte[] metadata,
                            final int expectedSelectors, final int expectedResources, final JSONObject report) throws Exception {
    final var catalog = new JSONObject(new String(metadata, StandardCharsets.UTF_8)); require(catalog.getInt("format") == 1, "Catalog format");
    final var entries = catalog.getJSONArray("sprites"); require(entries.length() == expectedSelectors, "Exact expected selector count");
    final Map<String, Image> images = new HashMap<>(); final Map<String, String> outputs = new HashMap<>();
    final Set<NativeUiQuad> physical = new HashSet<>(); final List<Glyph> result = new ArrayList<>();
    final var families = new JSONObject(); final var inventory = new JSONArray();
    for(int i = 0; i < entries.length(); i++) {
      final var entry = entries.getJSONObject(i); final String family = entry.getString("family"), name = entry.getString("resource");
      final Source source = sources.get(family); require(source != null, "Unsupported source family: " + family);
      require(hash(source.event.source()).equals(entry.getString("sourceSha256")), "Actual raw/composite source pin: " + entry.getString("id"));
      if(entry.has("sourceImageSha256")) require(hash(source.imageInput).equals(entry.getString("sourceImageSha256")), "Raw image audit pin");
      if(entry.has("sourcePaletteSha256")) require(hash(source.paletteInput).equals(entry.getString("sourcePaletteSha256")), "Raw palette audit pin");
      final var paletteRect = source.event.tim().getClutRect();
      require(entry.getInt("palette") < paletteRect.w / 16 * paletteRect.h, "Actual palette denominator");
      final var crop = entry.getJSONArray("crop"); final Image indices = sourceIndices(source.event.tim(), crop.getInt(0), crop.getInt(1), crop.getInt(2), crop.getInt(3));
      final NativeUiQuad quad = quad(source.event, entry); require(physical.add(quad), "Duplicate physical selector: " + quad);
      if(!images.containsKey(name)) {
        final byte[] bytes = resource(provider, "/uihd/font/" + name); final String digest = hash(bytes);
        require(digest.equals(entry.getString("outputSha256")), "Packaged output pin: " + name);
        images.put(name, png(bytes)); outputs.put(name, digest);
        inventory.put(new JSONObject().put("resource", name).put("sha256", digest));
      }
      require(outputs.get(name).equals(entry.getString("outputSha256")), "Aliased resource hash inconsistent");
      final Image art = images.get(name); final var size = entry.getJSONArray("outputSize");
      require(art.width == size.getInt(0) && art.height == size.getInt(1), "PNG/catalog dimensions");
      require(art.width % indices.width == 0 && art.height % indices.height == 0
        && art.width / indices.width == art.height / indices.height && art.width / indices.width >= 2, "Uniform source metric scale");
      result.add(new Glyph(entry, source, quad, indices, art)); families.put(family, families.optInt(family) + 1);
    }
    require(images.size() == expectedResources, "Exact unique runtime resource count");
    report.put("selectors", entries.length()).put("uniquePngResources", images.size()).put("families", families).put("packagedResources", inventory);
    return result;
  }
  static Obj object(final Glyph glyph) {
    final NativeUiQuad q = glyph.quad;
    return new QuadBuilder("Prepared font exact crop").size(q.width(), q.height()).uv(q.u(), q.v()).bpp(Bpp.BITS_4)
      .vramPos(q.pageX(), q.pageY()).clut(q.clutX(), q.clutY()).build();
  }
  static void canvasAliases(final List<Glyph> glyphs, final JSONObject report) throws Exception {
    final var checked = new JSONArray();
    for(final Glyph alias : glyphs) {
      final String resource = alias.entry.getString("resource");
      if(!resource.startsWith("battle-level-perfect-level-") || !resource.endsWith("-postbattle.png")) continue;
      final String original = resource.replace("-postbattle.png", ".png");
      final Glyph base = glyphs.stream().filter(g -> g.entry.getString("resource").equals(original)).findFirst().orElseThrow();
      require(alias.indices.width == 8 && base.indices.width == 7 && alias.indices.height == 16 && base.indices.height == 16
        && alias.source.event.id.equals("battle_hud_2") && alias.entry.getInt("palette") == 1, "Postbattle canvas exact production crop/CLUT");
      require(alias.artwork.width == 64 && base.artwork.width == 56 && alias.artwork.height == 128 && base.artwork.height == 128, "Postbattle canvas exact scale");
      int sourceShadowRows = 0;
      for(int y = 0; y < alias.artwork.height; y++) {
        for(int x = 0; x < base.artwork.width; x++) for(int channel = 0; channel < 4; channel++) {
          require(base.artwork.data[(y * base.artwork.width + x) * 4 + channel] == alias.artwork.data[(y * alias.artwork.width + x) * 4 + channel], "Padded alias must preserve all approved seven-column artwork bytes");
        }
        final int nativeIndex = alias.indices.data[(y / 8 * 8 + 7) * 4] & 255;
        require(nativeIndex == 0 || nativeIndex == 15, "Eighth native column has an unexpected role");
        if(y % 8 == 0 && nativeIndex == 15) sourceShadowRows++;
        for(int x = 56; x < 64; x++) {
          final int p = (y * 64 + x) * 4, alpha = alias.artwork.data[p + 3] & 255;
          require(alpha == (nativeIndex == 0 ? 0 : 255) && (nativeIndex == 0 || (alias.artwork.data[p] & 255) == nativeIndex), "Eighth-column live palette shadow equals original role/run, without stretch");
        }
      }
      checked.put(new JSONObject().put("resource", resource).put("base", original).put("unchangedSevenColumnArtworkSha256", base.entry.getString("outputSha256"))
        .put("outputSha256", alias.entry.getString("outputSha256")).put("nativeRightColumnShadowRows", sourceShadowRows));
    }
    require(checked.length() == 6, "Exactly six postbattle canvas aliases verified"); report.put("postbattleCanvasAliases", checked);
  }
  static NativeSpriteFont.Selection selected(final Glyph glyph) throws Exception {
    NativeSpriteFont.Selection selection = NativeSpriteFont.select(glyph.quad);
    // A large glyph can require several small cached aliases to retire before its allocation fits.
    for(int attempts = 0; selection == null && attempts < 2048; attempts++) { reset(); selection = NativeSpriteFont.select(glyph.quad); }
    require(selection != null && selection.current(), "Every packaged selector must load: " + glyph.entry.getString("id"));
    @SuppressWarnings("unchecked") final var regions = (Map<NativeUiQuad, ?>)field(null, NativeSpriteFont.class, "REGIONS");
    final Object region = regions.get(glyph.quad); final Image indices = (Image)field(region, region.getClass(), "indices");
    require(Arrays.equals(indices.data, glyph.indices.data), "Runtime indexed reference equals independent source word crop");
    @SuppressWarnings("unchecked") final var supplier = (java.util.function.Supplier<Image>)field(region, region.getClass(), "artwork");
    final Image decoded = supplier.get(); require(Arrays.equals(decoded.data, glyph.artwork.data), "Runtime decoded PNG equals independent RGBA decoder");
    return selection;
  }
  static void invoke(final Object module, final Method listener, final NativeUiTextureEvent event) throws Exception { listener.invoke(module, event); }
  static void clear() throws Exception { NativeSpriteFont.clear(); NativeUiTextures.clear(); reset(); }
  private static void headless(final Object module, final Method listener, final List<Glyph> glyphs, final JSONObject report) throws Exception {
    final var sourceReports = new JSONArray(); int queues = 0;
    for(final String family : glyphs.stream().map(g -> g.source.event.id).distinct().toList()) {
      clear(); final var familyGlyphs = glyphs.stream().filter(g -> g.source.event.id.equals(family)).toList();
      final var event = familyGlyphs.getFirst().source.event; invoke(module, listener, event);
      require(NativeSpriteFont.selectionCount() == familyGlyphs.size(), "All declared physical aliases registered: " + family);
      for(final Glyph glyph : familyGlyphs) {
        reset(); final var selection = selected(glyph); final Obj obj = object(glyph);
        try {
          final var queued = GameEngine.RENDERER.queueUiOrthoModel(obj, new MV(), QueuedModelStandard.class); queued.hasTranslucency();
          require(field(queued, QueuedModelStandard.class, "spriteFont") != null, "Actual eligible UI queue selects packaged glyph");
          final var custom = GameEngine.RENDERER.queueUiOrthoModel(obj, new MV(), QueuedModelStandard.class).texture(selection.artwork(), 0); custom.hasTranslucency();
          require(field(custom, QueuedModelStandard.class, "spriteFont") == null, "Caller texture override stays authoritative"); queues++;
        } finally { reset(); obj.delete(); }
      }
      final var one = familyGlyphs.getFirst(); final var token = selected(one); clear();
      require(!token.current() && NativeSpriteFont.select(one.quad) == null && NativeSpriteFont.allocatedBytes() == 0, "Disabled provider retires source/GPU and restores absence");
      final byte[] changed = event.source(); changed[changed.length - 1] ^= 1;
      invoke(module, listener, new NativeUiTextureEvent(event.id, new Tim(new FileData(changed)), event.imageX, event.imageY, event.clutX, event.clutY, event.clutRows));
      require(NativeSpriteFont.selectionCount() == 0, "Wrong source hash cannot retain old glyph selection");
      invoke(module, listener, event); require(selected(one) != null, "Exact event re-enable restores source-bound resource");
      sourceReports.put(new JSONObject().put("family", family).put("sourceSha256", hash(event.source())).put("rawImageSha256", hash(one.source.imageInput))
        .put("rawPaletteSha256", hash(one.source.paletteInput)).put("imageX", event.imageX).put("imageY", event.imageY)
        .put("clutX", event.clutX).put("clutY", event.clutY).put("clutRows", event.clutRows).put("selected", familyGlyphs.size()));
    }
    clear(); report.put("actualUiQueues", queues).put("sourceEvents", sourceReports).put("fallbackControls", "each family: wrong source, clear/retirement, exact re-enable; every selector: caller override");
  }
  public static void main(final String[] args) throws Throwable {
    if(args.length != 6 && args.length != 7) throw new IllegalArgumentException("Supply private files root, actual UIHD JAR, NEW private proof directory, expected catalog SHA, exact selector count, exact PNG count; optional windowless JNI");
    final Path proof = Path.of(args[2]).toAbsolutePath(); for(Path p = proof; p != null; p = p.getParent()) require(!Files.isSymbolicLink(p), "Linked proof destination"); Files.createDirectory(proof);
    final var report = new JSONObject().put("verdict", "FAIL").put("scope", "all declared prepared catalog selectors with actual packaged provider; source data remains private; no game, campaign or Deck acceptance");
    final var api = RenderEngine.class.getDeclaredField("api"); api.setAccessible(true); final Object previous = api.get(GameEngine.RENDERER);
    Throwable failure = null;
    try(final var loader = new URLClassLoader(new java.net.URL[]{Path.of(args[1]).toUri().toURL()}, UiHdPreparedFontCatalogProbe.class.getClassLoader())) {
      ((org.legendofdragoon.modloader.events.EventManager.Access)field(null, GameEngine.class, "EVENT_ACCESS")).initialize(GameEngine.MODS);
      if(!CoreMod.SHADER_ENABLE_CRT_CONFIG.isValid()) CoreMod.registerConfig(new ConfigRegistryEvent((legend.game.saves.ConfigRegistry)GameEngine.REGISTRIES.config));
      api.set(GameEngine.RENDERER, new NoopApi());
      final Class<?> provider = Class.forName("uihd.UiHdMod", false, loader);
      require(Path.of(provider.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(Path.of(args[1]).toRealPath()), "Actual UIHD JAR class provenance");
      final byte[] metadata = resource(provider, "/uihd/font/native-sprites.json"); require(hash(metadata).equals(args[3]), "Frozen catalog SHA");
      report.put("uihdJarSha256", hash(bounded(Path.of(args[1]), 128 * 1024 * 1024))).put("catalogSha256", hash(metadata));
      final var glyphs = catalog(provider, sources(Path.of(args[0])), metadata, Integer.parseInt(args[4]), Integer.parseInt(args[5]), report);
      canvasAliases(glyphs, report);
      final Object module = provider.getConstructor().newInstance(); final Method listener = provider.getMethod("nativeTexture", NativeUiTextureEvent.class);
      final var font = new legend.core.font.FontSelectionEvent(); provider.getMethod("font", legend.core.font.FontSelectionEvent.class).invoke(module, font);
      require(font.font() instanceof legend.core.font.PackagedFont, "Actual packaged provider retains Original HD default offer");
      require(((java.util.Map<?, ?>)field(font.font(), legend.core.font.Font.class, "glyphs")).size() == 139, "Existing 139 logical glyphs retained");
      report.put("sharedOriginalHdGlyphs", 139);
      // A queue captures its shader on allocation: create the GPU pool before any Noop queues.
      if(args.length == 7) {
        api.set(GameEngine.RENDERER, previous);
        UiHdPreparedFontGpuProbe.run(args[6], module, listener, glyphs, proof, report);
        api.set(GameEngine.RENDERER, new NoopApi());
      }
      headless(module, listener, glyphs, report); api.set(GameEngine.RENDERER, previous);
      report.put("verdict", "PASS").put("activeRouteLimit", "Direct eligible production renderer queues exercise catalog availability; eight dormant action words are not asserted active. Production caller tests are separate.");
    } catch(final Throwable problem) { failure = problem; report.put("error", problem.toString()); }
    finally {
      try { clear(); api.set(GameEngine.RENDERER, previous); legend.core.Async.shutdown(); } catch(final Throwable problem) { if(failure == null) failure = problem; else failure.addSuppressed(problem); report.put("verdict", "FAIL").put("cleanupError", problem.toString()); }
      Files.writeString(proof.resolve("receipt.json"), report.toString(2) + "\n", StandardOpenOption.CREATE_NEW);
    }
    if(failure != null) throw failure; System.out.println("Prepared packaged catalog PASS " + report.getInt("selectors") + " selectors / " + report.getInt("uniquePngResources") + " PNGs");
  }
}
