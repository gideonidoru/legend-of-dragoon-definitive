package legend.definitive.effects;

import legend.core.gpu.Gpu;
import legend.core.gpu.Rect4i;
import legend.game.textures.Image;
import legend.game.tim.Tim;
import legend.game.unpacker.FileData;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

/** Original fixtures: copies, native writes, snapshots and mod changes, with no graphics context. */
class IndexedEffectVramTest {
  private static final Rect4i RECT = new Rect4i(4, 10, 2, 1);
  private static final String HASH = "a".repeat(64);
  private static Image detail() {
    final byte[] rgba = new byte[16 * 2 * 4];
    for(int y = 0; y < 2; y++) for(int x = 0; x < 16; x++) {
      final int p = (y * 16 + x) * 4;
      rgba[p] = (byte)(x / 2 % 4); rgba[p + 1] = (byte)(y + 5); rgba[p + 2] = (byte)(x + y); rgba[p + 3] = (byte)255;
    }
    return new Image(rgba, 16, 2);
  }
  private static int pixel(final IndexedEffectVram state, final int word, final int x, final int y) {
    return state.pixels()[word / 1024 * 2 * IndexedEffectVram.WIDTH + word % 1024 * 4 + y * IndexedEffectVram.WIDTH + x];
  }
  private static void sameWord(final IndexedEffectVram state, final int source, final int destination) {
    for(int y = 0; y < 2; y++) for(int x = 0; x < 4; x++) assertEquals(pixel(state, source, x, y), pixel(state, destination, x, y));
  }
  private static IndexedEffectVram registered() {
    final var state = new IndexedEffectVram(); state.register(RECT, HASH, detail()); return state;
  }

  @Test void packedSubtexelsKeepDistinctCoordinatesAndUseExactlySixteenMiB() {
    final var state = registered();
    assertEquals(16L * 1024 * 1024, IndexedEffectVram.DETAIL_BYTES);
    final int p = pixel(state, 10 * 1024 + 4, 0, 0);
    assertEquals(0x8050, p & 65535); assertEquals(0x8150, p >>> 16);
    assertNotEquals(p, pixel(state, 10 * 1024 + 4, 0, 1));
    assertEquals(1024, state.takeDirtyRows().cardinality());
    assertTrue(state.takeDirtyRows().isEmpty());
  }
  @Test void sourceIndexValidationHandlesDiscardVisibleBlackAndPaddedPaletteBlocks() {
    final ByteBuffer bytes = ByteBuffer.allocate(98).order(ByteOrder.LITTLE_ENDIAN);
    bytes.putInt(16).putInt(8).putInt(76).putShort((short)0).putShort((short)300).putShort((short)16).putShort((short)1);
    for(int i = 0; i < 16; i++) bytes.putShort((short)(i == 2 ? 0x8000 : i));
    bytes.position(84); bytes.putInt(14).putShort((short)4).putShort((short)10).putShort((short)1).putShort((short)1).putShort((short)0x3210);
    final Tim tim = new Tim(new FileData(bytes.array()));
    final Image full = detail();
    final byte[] crop = new byte[8 * 2 * 4];
    System.arraycopy(full.data, 0, crop, 0, 32); System.arraycopy(full.data, 64, crop, 32, 32);
    final Image valid = new Image(crop, 8, 2);
    assertDoesNotThrow(() -> IndexedEffectVram.validate(tim, valid));
    crop[0] = 4; assertThrows(IllegalArgumentException.class, () -> IndexedEffectVram.validate(tim, valid));
  }
  @Test void partialWritesInvalidateOnlyWrittenWordsAndObsoleteReselectionCannotReturn() {
    final var state = registered(); final int word = 10 * 1024 + 4;
    final int old = pixel(state, word + 1, 0, 0); final var source = state.sources().getFirst();
    state.takeDirtyRows(); state.invalidateWord(word);
    assertEquals(0, pixel(state, word, 0, 0)); assertEquals(old, pixel(state, word + 1, 0, 0));
    assertEquals(2, state.takeDirtyRows().cardinality());
    state.invalidateWord(word + 1); assertTrue(state.sources().isEmpty());
    state.reselect(source.id, detail()); assertEquals(0, pixel(state, word, 0, 0));
  }
  @Test void paletteWritesOutsideTheImageDoNotInvalidateDetail() {
    final var state = registered(); final int word = 10 * 1024 + 4;
    final int old = pixel(state, word, 0, 0); state.takeDirtyRows();
    state.invalidateWord(300 * 1024); assertEquals(old, pixel(state, word, 0, 0)); assertTrue(state.takeDirtyRows().isEmpty());
  }
  @Test void overlappingTextureAnimationsFollowNativeTraversalAndRetainSourceOffsets() {
    final var state = registered(); final int word = 10 * 1024 + 4;
    state.copyWord(word, word + 1); state.copyWord(word + 1, word + 2);
    sameWord(state, word, word + 2);
    final var source = state.sources().getFirst(); state.reselect(source.id, null);
    assertEquals(0, pixel(state, word + 2, 0, 0));
    state.reselect(source.id, detail()); sameWord(state, word, word + 2);
    state.copyWord(word, word); assertNotEquals(0, pixel(state, word, 0, 0));
  }
  @Test void snapshotSurvivesMenuOverwritesAndHonorsModChangesDuringTheMenu() {
    final var state = registered(); final int word = 10 * 1024 + 4;
    final var source = state.sources().getFirst(); final var snapshot = state.capture(RECT);
    state.invalidateWord(word); state.invalidateWord(word + 1);
    assertEquals(1, state.sources().size());
    state.reselect(source.id, null); state.restore(snapshot);
    assertEquals(0, pixel(state, word, 0, 0));
    state.reselect(source.id, detail()); assertNotEquals(0, pixel(state, word, 0, 0));
    state.restore(snapshot); // Released snapshots cannot restore stale artwork twice.
    state.invalidateWord(word); state.invalidateWord(word + 1); assertTrue(state.sources().isEmpty());
  }
  @Test void snapshotsAreBoundedAndAbandonedScenesReleaseTheirReferences() {
    final var state = registered(); final int word = 10 * 1024 + 4;
    final var one = state.capture(RECT); final var two = state.capture(RECT);
    assertThrows(IllegalArgumentException.class, () -> state.capture(RECT));
    assertDoesNotThrow(() -> state.rotateRows(RECT, 1)); // Animation is valid even while two world-menu backups are retained.
    state.invalidateWord(word); state.invalidateWord(word + 1);
    state.release(one); state.release(one); assertEquals(1, state.sources().size());
    state.release(two); assertTrue(state.sources().isEmpty());
    assertThrows(IllegalArgumentException.class, () -> state.capture(new Rect4i(0, 0, 256, 256)));
  }
  @Test void delayedSelectionCannotReclaimIdenticalOverwritesOrNewerUploads() throws Exception {
    for(final boolean newerTim : new boolean[]{false,true}) {
      final Gpu gpu = new Gpu();
      final ByteBuffer bytes = ByteBuffer.allocate(68).order(ByteOrder.LITTLE_ENDIAN);
      bytes.putInt(16).putInt(8).putInt(44).putShort((short)0).putShort((short)300).putShort((short)16).putShort((short)1);
      for(int i = 0; i < 16; i++) bytes.putShort((short)i);
      bytes.putInt(16).putShort((short)4).putShort((short)10).putShort((short)2).putShort((short)1).putShort((short)0x3210).putShort((short)0x3210);
      final Tim tim = new Tim(new FileData(bytes.array()));
      final var entered = new java.util.concurrent.CountDownLatch(1);
      final var release = new java.util.concurrent.CountDownLatch(1);
      final Image current = detail(); current.data[2] = 127;
      try(final var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
        final var pending = worker.submit(() -> gpu.uploadEffectTim(tim, event -> {
          entered.countDown();
          try { if(!release.await(3, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Fixture callback timed out"); }
          catch(final InterruptedException error) { throw new RuntimeException(error); }
          event.detail = detail();
        }));
        try {
          assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS));
          if(newerTim) gpu.uploadEffectTim(tim, event -> event.detail = current);
          else gpu.uploadData15(RECT, new int[]{0x3210,0x3210});
        } finally { release.countDown(); }
        pending.get(3, java.util.concurrent.TimeUnit.SECONDS);
      }
      final var field = Gpu.class.getDeclaredField("effectVram"); field.setAccessible(true);
      final var state = (IndexedEffectVram)field.get(gpu);
      if(newerTim) {
        assertEquals(1, state.sources().size()); assertEquals(127, pixel(state, 10 * 1024 + 4, 0, 0) >>> 8 & 127);
      } else { assertTrue(state.sources().isEmpty()); assertFalse(state.allocated()); }
      assertEquals(0x3210, gpu.getPixel15(4,10));
    }
  }
  @Test void paletteUploadCannotResurrectOverlappingImageWordsEvenWhenIndicesMatch() throws Exception {
    final Gpu gpu = new Gpu();
    final ByteBuffer bytes = ByteBuffer.allocate(68).order(ByteOrder.LITTLE_ENDIAN);
    bytes.putInt(16).putInt(8).putInt(44).putShort((short)4).putShort((short)10).putShort((short)16).putShort((short)1);
    for(int i = 0; i < 16; i++) bytes.putShort((short)0x3210);
    bytes.putInt(16).putShort((short)4).putShort((short)10).putShort((short)2).putShort((short)1).putShort((short)0x3210).putShort((short)0x3210);
    gpu.uploadEffectTim(new Tim(new FileData(bytes.array())), event -> event.detail = detail());
    final var field = Gpu.class.getDeclaredField("effectVram"); field.setAccessible(true);
    final var state = (IndexedEffectVram)field.get(gpu);
    assertEquals(0x3210,gpu.getPixel15(4,10)); assertTrue(state.sources().isEmpty()); assertFalse(state.allocated());
  }
  @Test void delayedSelectionCannotUndoDisableOrNewerModSelection() throws Exception {
    for(final boolean reenable : new boolean[]{false,true}) {
      final Gpu gpu = new Gpu();
      final ByteBuffer bytes = ByteBuffer.allocate(68).order(ByteOrder.LITTLE_ENDIAN);
      bytes.putInt(16).putInt(8).putInt(44).putShort((short)0).putShort((short)300).putShort((short)16).putShort((short)1);
      for(int i = 0; i < 16; i++) bytes.putShort((short)i);
      bytes.putInt(16).putShort((short)4).putShort((short)10).putShort((short)2).putShort((short)1).putShort((short)0x3210).putShort((short)0x3210);
      final Tim tim = new Tim(new FileData(bytes.array()));
      final var entered = new java.util.concurrent.CountDownLatch(1);
      final var release = new java.util.concurrent.CountDownLatch(1);
      final Image current = detail(); current.data[2] = 127;
      try(final var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
        final var pending = worker.submit(() -> gpu.uploadEffectTim(tim, event -> {
          entered.countDown();
          try { if(!release.await(3, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Fixture callback timed out"); }
          catch(final InterruptedException error) { throw new RuntimeException(error); }
          event.detail = detail();
        }));
        try {
          assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS));
          gpu.reselectEffectArtwork(event -> event.detail = null);
          if(reenable) gpu.reselectEffectArtwork(event -> event.detail = current);
        } finally { release.countDown(); }
        pending.get(3, java.util.concurrent.TimeUnit.SECONDS);
      }
      final var field = Gpu.class.getDeclaredField("effectVram"); field.setAccessible(true);
      final var state = (IndexedEffectVram)field.get(gpu);
      assertEquals(1,state.sources().size());
      if(reenable) assertEquals(127,pixel(state,10 * 1024 + 4,0,0) >>> 8 & 127);
      else assertFalse(state.allocated());
    }
  }
  @Test void signedCpuRowRotationsPreserveNativePixelsAndCopiedSourceOffsets() throws Exception {
    final Gpu gpu = new Gpu();
    final Rect4i rect = new Rect4i(4, 10, 1, 3);
    gpu.uploadData15(rect, new int[]{0x3210, 0x7654, 0xba98});
    final var field = Gpu.class.getDeclaredField("effectVram"); field.setAccessible(true);
    final var state = (IndexedEffectVram)field.get(gpu);
    final byte[] rgba = new byte[8 * 6 * 4];
    for(int y = 0; y < 6; y++) for(int x = 0; x < 8; x++) {
      final int i = (y * 8 + x) * 4;
      rgba[i] = (byte)(y / 2 * 4 + x / 2); rgba[i + 1] = 15; rgba[i + 2] = (byte)(y + 1); rgba[i + 3] = (byte)255;
    }
    final Image image = new Image(rgba, 8, 6); state.register(rect, HASH, image);
    final int initial = pixel(state, 10 * 1024 + 4, 0, 0);
    gpu.rotateVramRows(rect, 1);
    assertEquals(0xba98, gpu.getPixel15(4, 10)); assertEquals(0x3210, gpu.getPixel15(4, 11));
    assertEquals(initial, pixel(state, 11 * 1024 + 4, 0, 0));
    final var source = state.sources().getFirst(); state.reselect(source.id, null); state.reselect(source.id, image);
    assertEquals(initial, pixel(state, 11 * 1024 + 4, 0, 0));
    gpu.rotateVramRows(rect, -4);
    assertEquals(0x3210, gpu.getPixel15(4, 10)); assertEquals(initial, pixel(state, 10 * 1024 + 4, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> gpu.rotateVramRows(new Rect4i(1023, 511, 2, 1), 1));
    assertEquals(0x3210, gpu.getPixel15(4, 10));
  }
  @Test void actualGpuMaskedCopyAndWritesPreserveOriginalNativePixels() throws Exception {
    final Gpu gpu = new Gpu(); final int word = 10 * 1024 + 4;
    gpu.uploadData15(RECT, new int[]{0x3210, 0x3210});
    final var field = Gpu.class.getDeclaredField("effectVram"); field.setAccessible(true);
    final var state = (IndexedEffectVram)field.get(gpu); state.register(RECT, HASH, detail());
    gpu.copyVramToVram(4, 10, 8, 10, 2, 1); sameWord(state, word, word + 4);
    assertEquals(0x3210, gpu.getPixel15(8, 10));
    gpu.uploadData15(new Rect4i(8, 10, 1, 1), new int[]{0x8000});
    gpu.status.drawPixels = Gpu.DRAW_PIXELS.NOT_TO_MASKED_AREAS;
    gpu.copyVramToVram(4, 10, 8, 10, 1, 1);
    assertEquals(0x8000, gpu.getPixel15(8, 10)); assertEquals(0, pixel(state, word + 4, 0, 0));
    gpu.uploadData15(new Rect4i(4, 10, 1, 1), new int[]{0x7654});
    assertEquals(0x7654, gpu.getPixel15(4, 10)); assertEquals(0, pixel(state, word, 0, 0));
  }
}
