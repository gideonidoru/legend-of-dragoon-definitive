package legend.definitive.font;

import legend.core.GameEngine;
import legend.core.gpu.Rect4i;
import legend.core.renderer.*;
import legend.core.renderer.noop.NoopApi;
import legend.game.Menus;
import legend.game.SItem;
import legend.game.combat.effects.AdditionCharEffectData0c;
import legend.game.combat.effects.AdditionNameTextEffect1c;
import legend.game.combat.ui.BattleDisplayStats144;
import legend.game.combat.ui.BattleHud;
import legend.game.inventory.screens.Control;
import legend.game.inventory.screens.PostBattleScreen;
import legend.game.textures.*;
import legend.game.tim.Tim;
import legend.game.types.*;
import legend.game.unpacker.FileData;
import org.joml.Matrix4f;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

import static legend.game.Scus94491BpeSegment.battleUiParts;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic pixels, actual production builders/queues. No window, driver, source disc or retail-art claim. */
class PreparedNativeFontRouteTest {
  private Object oldApi;
  private UiFile oldUiFile;
  private Obj oldBattleParts;
  private int oldWidth, oldHeight;
  private Map<String, NativeUiTextureEvent> oldSources;
  private long oldSourceBytes;
  private boolean oldElementIcons;

  private static Field field(final Class<?> type, final String name) throws Exception {
    final Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
  }
  private static void reset() throws Exception {
    final var reset = RenderEngine.class.getDeclaredMethod("resetBatches"); reset.setAccessible(true); reset.invoke(GameEngine.RENDERER);
  }
  private static List<QueuedModelStandard> queued() throws Exception {
    final var batch = (RenderBatch)field(RenderEngine.class, "mainBatch").get(GameEngine.RENDERER);
    final var result = new ArrayList<QueuedModelStandard>();
    for(int i = 0; i < batch.orthoPool.size(); i++) result.add((QueuedModelStandard)batch.orthoPool.get(i));
    return result;
  }
  private static NativeUiQuad source(final QueuedModelStandard model) throws Exception {
    final var obj = (Obj)field(QueuedModel.class, "obj").get(model);
    return obj.nativeUiQuad(field(QueuedModel.class, "startVertex").getInt(model), field(QueuedModel.class, "vertexCount").getInt(model)).overridden(model);
  }
  private static Matrix4f transform(final QueuedModelStandard model) throws Exception {
    return (Matrix4f)field(QueuedModel.class, "transforms").get(model);
  }
  private static Image image(final int w, final int h) {
    final byte[] pixels = new byte[w * h * 4];
    for(int i = 0; i < pixels.length; i += 4) { pixels[i] = 1; pixels[i + 3] = (byte)255; }
    return new Image(pixels, w, h);
  }
  private void assertSelected(final QueuedModelStandard model, final NativeUiQuad expected) throws Exception {
    assertEquals(expected, source(model));
    assertTrue(NativeSpriteFont.register(this, "route", expected, image((int)expected.width(), (int)expected.height()), image((int)expected.width() * 2, (int)expected.height() * 2)));
    model.hasTranslucency();
    assertNotNull(field(QueuedModelStandard.class, "spriteFont").get(model), "Actual production draw must select its exact crop/CLUT before pass classification");
    NativeSpriteFont.clear(); model.hasTranslucency();
    assertNull(field(QueuedModelStandard.class, "spriteFont").get(model), "Disabling selection restores the native route");
  }
  @BeforeEach void setup() throws Exception {
    final var access = field(GameEngine.class, "EVENT_ACCESS").get(null);
    ((org.legendofdragoon.modloader.events.EventManager.Access)access).initialize(GameEngine.MODS);
    if(!legend.game.modding.coremod.CoreMod.SHADER_ENABLE_CRT_CONFIG.isValid()) legend.game.modding.coremod.CoreMod.registerConfig(new legend.game.saves.ConfigRegistryEvent((legend.game.saves.ConfigRegistry)GameEngine.REGISTRIES.config));
    oldElementIcons = GameEngine.CONFIG.getConfig(legend.game.modding.coremod.CoreMod.DISPLAY_ELEMENT_ICON_CONFIG.get());
    GameEngine.CONFIG.setConfig(legend.game.modding.coremod.CoreMod.DISPLAY_ELEMENT_ICON_CONFIG.get(), false);
    oldApi = field(RenderEngine.class, "api").get(GameEngine.RENDERER); field(RenderEngine.class, "api").set(GameEngine.RENDERER, new NoopApi());
    oldUiFile = Menus.uiFile_800bdc3c;
    oldBattleParts = (Obj)field(BattleUiParts.class, "obj").get(battleUiParts);
    oldWidth = legend.game.Graphics.displayWidth_1f8003e0; oldHeight = legend.game.Graphics.displayHeight_1f8003e4;
    legend.game.Graphics.displayWidth_1f8003e0 = 320; legend.game.Graphics.displayHeight_1f8003e4 = 240;
    @SuppressWarnings("unchecked") final var sources = (Map<String, NativeUiTextureEvent>)field(NativeUiSources.class, "sources").get(NativeUiSources.CURRENT);
    oldSources = new LinkedHashMap<>(sources); oldSourceBytes = field(NativeUiSources.class, "bytes").getLong(NativeUiSources.CURRENT); sources.clear(); field(NativeUiSources.class, "bytes").setLong(NativeUiSources.CURRENT, 0);
    NativeSpriteFont.clear(); reset();
  }
  @AfterEach void cleanup() throws Exception {
    Menus.deallocateRenderables(0xff); Menus.renderUi(); NativeSpriteFont.clear(); reset();
    if(Menus.uiFile_800bdc3c != oldUiFile && Menus.uiFile_800bdc3c != null) Menus.uiFile_800bdc3c.delete();
    Menus.uiFile_800bdc3c = oldUiFile;
    final var parts = (Obj)field(BattleUiParts.class, "obj").get(battleUiParts); if(parts != null && parts != oldBattleParts) parts.delete(); field(BattleUiParts.class, "obj").set(battleUiParts, oldBattleParts);
    @SuppressWarnings("unchecked") final var sources = (Map<String, NativeUiTextureEvent>)field(NativeUiSources.class, "sources").get(NativeUiSources.CURRENT); sources.clear(); sources.putAll(oldSources); field(NativeUiSources.class, "bytes").setLong(NativeUiSources.CURRENT, oldSourceBytes);
    legend.game.Graphics.displayWidth_1f8003e0 = oldWidth; legend.game.Graphics.displayHeight_1f8003e4 = oldHeight;
    GameEngine.CONFIG.setConfig(legend.game.modding.coremod.CoreMod.DISPLAY_ELEMENT_ICON_CONFIG.get(), oldElementIcons);
    field(RenderEngine.class, "api").set(GameEngine.RENDERER, oldApi);
  }
  @Test void actualLabelCompositeAndBothSlashesRetainScissorsDepthAndSelection() throws Exception {
    final var hud = new BattleHud(null); final var stats = new BattleDisplayStats144(); stats.x_00 = 100; stats.y_02 = 200;
    final var build = BattleHud.class.getDeclaredMethod("buildStatLabels", int.class); build.setAccessible(true); build.invoke(hud, 1);
    field(BattleHud.class, "hudFade").setInt(hud, 6);
    final var queue = BattleHud.class.getDeclaredMethod("queueStatLabel", BattleDisplayStats144.class, int.class, int.class, boolean.class, boolean.class, int.class); queue.setAccessible(true);
    for(int label = 0; label < 3; label++) {
      final var model = (QueuedModelStandard)queue.invoke(hud, stats, 0, label, true, true, 2);
      assertSelected(model, new NativeUiQuad(704, 256, 736, 508, label == 2 ? 0 : 96, 32, label == 2 ? 16 : 8, label == 2 ? 32 : 8));
      assertEquals(124, transform(model).m32()); assertEquals(label == 0 ? 185 : label == 1 ? 195 : 181, transform(model).m31());
    }
    for(final boolean hasDragoon : new boolean[]{false, true}) {
      final var model = (QueuedModelStandard)queue.invoke(hud, stats, 0, 2, hasDragoon, false, 2);
      final Rect4i scissor = model.modelScissor(); assertEquals(82, scissor.x); assertEquals(181, scissor.y); assertEquals(320, scissor.w); assertEquals(hasDragoon ? 24 : 12, scissor.h);
    }
    ((Obj)field(BattleHud.class, "nameAndPortraitObj").get(hud)).delete();
  }
  @Test void actualLargeFloatingNumbersAndMissDrawExactNativeQuads() throws Exception {
    final var hud = new BattleHud(null);
    for(int digit = 0; digit < 10; digit++) hud.addFloatingNumber(digit, 0, 0, digit, 150, 70, 30, 1);
    hud.addFloatingNumber(10, 0, 0, -1, 150, 70, 30, 1);
    for(int tick = 0; tick < 4; tick++) hud.tickFloatingNumbers();
    hud.drawFloatingNumbers();
    final var models = queued(); assertEquals(11, models.size());
    for(int i = 0; i < models.size(); i++) assertSelected(models.get(i), new NativeUiQuad(704, 256, 896, 312, i == 10 ? 72 : 16 + i * 8, i == 10 ? 128 : 40, i == 10 ? 36 : 8, 16));
    for(final String name : List.of("type1FloatingDigits", "type3FloatingDigits")) for(final Obj obj : (Obj[])field(BattleHud.class, name).get(hud)) obj.delete();
    ((Obj)field(BattleHud.class, "miss").get(hud)).delete();
  }
  @Test void actualAdditionUnknownFallbackSpAndPointsUseTheirDistinctPaletteAndFullSourceWidth() throws Exception {
    battleUiParts.init(); final var effect = new AdditionNameTextEffect1c(); effect.additionName_02 = "A?"; effect.totalSp_10 = 30; effect.ticks_04 = 25;
    final var character = new AdditionCharEffectData0c(); character.position_04 = 20; character.offsetY_06 = 40;
    effect.renderAdditionNameChar(character, 128, 0); effect.renderAdditionNameChar(character, 128, 1); effect.renderAdditionSpGain(character, 128, 0);
    final var models = queued(); assertEquals(5, models.size());
    assertSelected(models.get(0), new NativeUiQuad(704, 256, 704, 506, 120, 144, 12, 12));
    assertSelected(models.get(1), new NativeUiQuad(704, 256, 704, 506, 84, 192, 12, 12));
    assertSelected(models.get(2), new NativeUiQuad(704, 256, 736, 505, 16, 40, 8, 16));
    assertSelected(models.get(3), new NativeUiQuad(704, 256, 736, 505, 40, 40, 8, 16));
    assertSelected(models.get(4), new NativeUiQuad(704, 256, 736, 505, 120, 40, 32, 16));
    assertEquals(2, transform(models.get(4)).m32(), "Existing Points centering/depth remains unchanged");
  }
  @Test void actualPerfectAndLevelWidthsRemainSeparateIncludingFlashingPalettes() throws Exception {
    battleUiParts.init();
    final int[] levelUs = {200, 208, 216, 208, 200, 224, 232, 240};
    final int[] perfectUs = {232, 208, 40, 48, 208, 56, 64, 240};
    for(int i = 0; i < 8; i++) {
      final var level = new BattleReportOverlay0e(); level.letterIndex = i; level.clut_0c = 0x4a; battleUiParts.queueLevelUp(level); battleUiParts.queueDaddyPerfect(i, 20, 30, 128);
    }
    final var models = queued(); assertEquals(16, models.size());
    for(int i = 0; i < 8; i++) {
      assertSelected(models.get(i * 2), new NativeUiQuad(704, 256, 896, 314, levelUs[i], 64, 7, 16));
      assertSelected(models.get(i * 2 + 1), new NativeUiQuad(704, 256, 736, 505, perfectUs[i], i == 2 || i == 3 || i == 5 || i == 6 ? 128 : 64, 8, 16));
    }
    for(int row = 2; row <= 3; row++) {
      final var level = new BattleReportOverlay0e(); level.clut_0c = 0x40 + row; battleUiParts.queueLevelUp(level);
      assertSelected(queued().getLast(), new NativeUiQuad(704, 256, 896, 304 + row, 200, 64, 7, 16));
    }
  }
  private static UiType menuType() {
    final var entries = new UiPart[219]; Arrays.setAll(entries, i -> new UiPart(new RenderableMetrics14[0], 1));
    for(int i = 0; i < 14; i++) {
      final int u = i < 13 ? 128 + i * 8 : 136, v = i < 13 ? 176 : 184;
      entries[i == 13 ? 218 : i] = new UiPart(new RenderableMetrics14[]{new RenderableMetrics14(u, v, 0, 0, i == 12 ? 31849 : i == 13 ? 64553 : 64617, 4140, 8, 8, 8, 8)}, 1);
    }
    final var type = new UiType(entries); type.obj = SItem.buildUiRenderable(type, "Synthetic numeric route"); return type;
  }
  @Test void actualMenuNumericComparisonSecondaryClutAndDecimalKeepFullColonAndScissor() throws Exception {
    final UiType type = menuType(); Menus.uiFile_800bdc3c = new UiFile(type, new UiType(new UiPart[0]), new UiType(new UiPart[0]), new UiType(new UiPart[0]));
    SItem.renderNumber(100, 50, 30, 2, 2); SItem.renderNumberComparison(100, 70, 29, 30, 2);
    final class Decimal extends Control {
      @Override protected void render(final int x, final int y) { this.renderNumber(x, y, ".", 1); }
    }
    new Decimal().render(100, 90); Menus.uploadRenderables(); Menus.renderUi();
    final var models = queued(); assertEquals(5, models.size());
    for(int i = 0; i < models.size(); i++) {
      final var s = source(models.get(i)); assertEquals(64, s.pageX()); assertEquals(256, s.pageY()); assertEquals(8, s.width()); assertEquals(8, s.height());
      assertEquals(transform(models.get(i)).m31() == 70 ? 176 : 144, s.clutX()); assertEquals(497, s.clutY());
      assertSelected(models.get(i), s); assertEquals(132, transform(models.get(i)).m32());
    }
    for(final int y : new int[]{50, 70}) {
      final var digitUs = new ArrayList<Float>();
      for(final var model : models) if(transform(model).m31() == y) digitUs.add(source(model).u());
      Collections.sort(digitUs); assertEquals(List.of(128f, 152f), digitUs, "Both actual routes draw all digits of 30");
    }
    final var decimal = models.stream().filter(model -> { try { return source(model).u() == 208; } catch(final Exception failure) { throw new RuntimeException(failure); } }).findFirst().orElseThrow();
    assertEquals(176, source(decimal).v(), "Decimal retains the source colon UV; no invented full-cell dot");
    assertEquals(4, decimal.modelScissor().h); assertEquals(95, decimal.modelScissor().y); assertEquals(91, transform(decimal).m31());
  }
  @Test void actualPostBattleRelocationUsesEightWideLevelCellsAndSmallDigits() throws Exception {
    final var entries = new UiPart[71]; Arrays.setAll(entries, i -> new UiPart(new RenderableMetrics14[0], 1));
    final int[] digitUs = {88, 16, 24, 32, 40, 48, 56, 64, 72, 80};
    for(int i = 0; i < 10; i++) entries[i + 3] = new UiPart(new RenderableMetrics14[]{new RenderableMetrics14(digitUs[i], 32, 0, 0, 30916, 44, 8, 8, 8, 8, 1, 1)}, 1);
    for(int i = 0; i < 6; i++) entries[i + 65] = new UiPart(new RenderableMetrics14[]{new RenderableMetrics14(200 + i * 8, 64, 0, 0, 30916, 44, 8, 16, 8, 16, 1, 1)}, 1);
    final var type = new UiType(entries); type.obj = SItem.buildUiRenderable(type, "Frozen postbattle cell geometry");
    final var empty = new UiType(new UiPart[0]); Menus.uiFile_800bdc3c = new UiFile(empty, empty, empty, type);
    final var screen = new PostBattleScreen();
    final var draw = PostBattleScreen.class.getDeclaredMethod("drawGlyph", int.class, int.class, int.class, int.class, int.class, int.class); draw.setAccessible(true);
    for(int i = 0; i < 10; i++) draw.invoke(screen, i + 3, i + 3, 20 + i * 8, 50, 736, 497);
    for(int i = 0; i < 6; i++) draw.invoke(screen, i + 65, i + 65, 20 + i * 8, 70, 736, 497);
    Menus.uploadRenderables(); Menus.renderUi(); final var models = queued(); assertEquals(16, models.size());
    final var seenDigits = new TreeSet<Float>(); final var seenLevel = new TreeSet<Float>();
    for(final var model : models) {
      final var quad = source(model); assertEquals(704, quad.pageX()); assertEquals(256, quad.pageY()); assertEquals(736, quad.clutX()); assertEquals(497, quad.clutY()); assertEquals(8, quad.width());
      if(quad.v() == 32) { assertEquals(8, quad.height()); seenDigits.add(quad.u()); }
      else { assertEquals(64, quad.v()); assertEquals(16, quad.height()); seenLevel.add(quad.u()); }
      assertSelected(model, quad);
    }
    assertEquals(new TreeSet<>(List.of(16f, 24f, 32f, 40f, 48f, 56f, 64f, 72f, 80f, 88f)), seenDigits);
    assertEquals(new TreeSet<>(List.of(200f, 208f, 216f, 224f, 232f, 240f)), seenLevel);
  }
  private static byte[] tim(final int imageX, final int wordWidth, final int height, final int rows, final int paletteX, final int paletteY, final int index) {
    final byte[] pixels = new byte[wordWidth * height * 2]; Arrays.fill(pixels, (byte)(index | index << 4));
    final var buffer = ByteBuffer.allocate(8 + 12 + rows * 32 + 12 + pixels.length).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putInt(16).putInt(8).putInt(12 + rows * 32).putShort((short)paletteX).putShort((short)paletteY).putShort((short)16).putShort((short)rows);
    for(int i = 0; i < rows * 16; i++) buffer.putShort((short)(i + 1));
    buffer.putInt(12 + pixels.length).putShort((short)imageX).putShort((short)256).putShort((short)wordWidth).putShort((short)height).put(pixels); return buffer.array();
  }
  @Test void actualMenuUploadPublishesNumericExtraPageAndReplaysSeparateImmutableSource() throws Exception {
    final byte[] container = new byte[67264];
    final int[] offsets = {0, 0x6200, 0x83e0, 0x10460, 0x10580};
    final byte[][] pages = {tim(576, 64, 192, 15, 656, 496, 1), tim(768, 2, 8, 1, 656, 496, 2), tim(640, 2, 8, 1, 672, 496, 3), tim(576, 0, 0, 4, 688, 496, 4), tim(576, 0, 0, 9, 688, 500, 5)};
    for(int i = 0; i < pages.length; i++) System.arraycopy(pages[i], 0, container, offsets[i], pages[i].length);
    final var load = SItem.class.getDeclaredMethod("menuAssetsLoaded", FileData.class, int.class); load.setAccessible(true); load.invoke(null, new FileData(container), 0);
    final var events = new HashMap<String, NativeUiTextureEvent>(); NativeUiSources.CURRENT.replay(event -> events.put(event.id, event));
    final var numeric = events.get("menu_numeric_extras"); assertNotNull(numeric, "Baseline announced only portrait source for these shared secondary palettes");
    final var character = events.get("menu_character_extras"); assertNotNull(character);
    assertEquals("menu_numeric_extras", numeric.slot); assertEquals(64, numeric.imageX); assertEquals(256, numeric.imageY); assertEquals(176, numeric.clutX); assertEquals(496, numeric.clutY); assertEquals(13, numeric.clutRows);
    assertArrayEquals(character.tim().getClutData().getBytes(), numeric.tim().getClutData().getBytes());
    assertArrayEquals(new Tim(new FileData(container)).getImageData().getBytes(), numeric.tim().getImageData().getBytes());
    assertFalse(Arrays.equals(character.tim().getImageData().getBytes(), numeric.tim().getImageData().getBytes()));
    final byte[] before = numeric.source(); container[5000] ^= 0x22; final byte[] returned = numeric.source(); returned[returned.length - 1] ^= 0x44;
    final var replay = new HashMap<String, NativeUiTextureEvent>(); NativeUiSources.CURRENT.replay(event -> replay.put(event.id, event));
    assertArrayEquals(before, replay.get(numeric.id).source(), "Caller mutation cannot retrofit cached source provenance");
    assertNotEquals(events.get("menu").slot, numeric.slot, "Secondary palette upload must not retire ordinary numeric/menu ownership");
  }
}
