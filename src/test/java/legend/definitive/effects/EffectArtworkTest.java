// Synthetic fixtures (2026-10-10), AGPL v3; no original game imagery.
package legend.definitive.effects;

import fxhd.FxHdMod;
import legend.core.gpu.Gpu;
import legend.core.renderer.Mesh;
import legend.core.renderer.MeshObj;
import legend.core.renderer.Texture;
import legend.core.renderer.TextureDataFormat;
import legend.core.renderer.TextureDataType;
import legend.core.renderer.TextureInternalFormat;
import legend.core.renderer.noop.NoopApi;
import legend.definitive.textures.TimImage;
import legend.game.modding.events.submap.EffectTextureEvent;
import legend.game.submap.AttachedSobjEffect;
import legend.game.submap.SMap;
import legend.game.submap.SmokeParticleEffect;
import legend.game.textures.Image;
import legend.game.tim.Tim;
import legend.game.unpacker.FileData;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class EffectArtworkTest {
  static byte[] tim(final int paletteY) {
    final ByteBuffer out = ByteBuffer.allocate(66).order(ByteOrder.LITTLE_ENDIAN);
    out.putInt(16).putInt(8).putInt(44).putShort((short)960).putShort((short)paletteY).putShort((short)16).putShort((short)1);
    for(int i = 0; i < 16; i++) out.putShort((short)(i == 2 ? 0x8000 : i == 3 ? 0x801f : i));
    out.putInt(14).putShort((short)976).putShort((short)320).putShort((short)1).putShort((short)1).putShort((short)0x3210);
    return out.array();
  }

  static Image scaled(final TimImage source) {
    final int width = source.width() * 2, height = source.height() * 2;
    final byte[] data = new byte[width * height * 4];
    for(int y = 0; y < height; y++) for(int x = 0; x < width; x++) {
      final int pixel = source.pixels()[y / 2 * source.width() + x / 2], p = (y * width + x) * 4;
      data[p] = (byte)(pixel >>> 16); data[p + 1] = (byte)(pixel >>> 8); data[p + 2] = (byte)pixel; data[p + 3] = (byte)(pixel >>> 24);
    }
    return new Image(data, width, height);
  }

  static EffectArtwork owned(final byte[] source) {
    final Texture texture = new NoopApi().makeTexture(null, "FxHD test", 8, 2, TextureInternalFormat.RGBA_8,
      TextureDataFormat.RGBA, TextureDataType.UBYTE, false, false, false, false);
    return new EffectArtwork(texture, new Tim(new FileData(source)));
  }

  @Test void cloudKeepsItsImageButUsesTheDustPaletteHeaderAndColours() throws Exception {
    final byte[] image = tim(474), palette = tim(465);
    palette[22] = 15;
    final byte[] imageControl = image.clone(), paletteControl = palette.clone();
    final byte[] paired = EffectArtwork.withPalette(image, palette);
    assertArrayEquals(Arrays.copyOfRange(image, 52, image.length), Arrays.copyOfRange(paired, 52, paired.length));
    assertArrayEquals(Arrays.copyOfRange(palette, 8, 52), Arrays.copyOfRange(paired, 8, 52));
    assertArrayEquals(imageControl, image); assertArrayEquals(paletteControl, palette);
    final Tim nativeTim = new Tim(new FileData(paired));
    assertTrue(EffectArtwork.bindingMatches(nativeTim, 31, 465 << 6 | 60, 64, 64));
    assertFalse(EffectArtwork.bindingMatches(nativeTim, 31, 474 << 6 | 60, 64, 64));
    assertFalse(EffectArtwork.bindingMatches(nativeTim, 31, 465 << 6 | 60, 68, 64));
    assertFalse(EffectArtwork.bindingMatches(nativeTim, 31 | 128, 465 << 6 | 60, 64, 64));
    assertThrows(IOException.class, () -> EffectArtwork.withPalette(Arrays.copyOf(image, 65), palette));
  }

  @Test void visibilityContractRejectsAlphaDiscardBlackAndDimensionChanges() throws Exception {
    final TimImage original = TimImage.read(tim(465), 0);
    final Image candidate = scaled(original);
    assertDoesNotThrow(() -> EffectArtwork.validate(original, candidate));
    candidate.data[3] = (byte)255;
    assertThrows(IOException.class, () -> EffectArtwork.validate(original, candidate));
    candidate.data[3] = 0; candidate.data[0] = 1;
    assertThrows(IOException.class, () -> EffectArtwork.validate(original, candidate));
    candidate.data[0] = 0; candidate.data[4 * 4] = 1;
    assertThrows(IOException.class, () -> EffectArtwork.validate(original, candidate));
    final Image discarded = scaled(original); Arrays.fill(discarded.data, 8, 12, (byte)0);
    assertThrows(IOException.class, () -> EffectArtwork.validate(original, discarded));
    assertThrows(IOException.class, () -> EffectArtwork.validate(original, new Image(new byte[16], 4, 1)));
  }

  @Test void liveVramChangesFallBackAndRestoreWithoutAllocatingAnotherTexture() {
    final Tim source = new Tim(new FileData(tim(465)));
    final Gpu gpu = new Gpu();
    gpu.uploadData15(source.getImageRect(), source.getImageData());
    gpu.uploadData15(source.getClutRect(), source.getClutData());
    final EffectArtwork artwork = owned(tim(465));
    assertTrue(artwork.matchesNative(gpu));
    final FileData changed = new FileData(source.getClutData().getBytes().clone()); changed.writeByte(2, (byte)8);
    gpu.uploadData15(source.getClutRect(), changed); assertFalse(artwork.matchesNative(gpu));
    gpu.uploadData15(source.getClutRect(), source.getClutData()); assertTrue(artwork.matchesNative(gpu));
    gpu.uploadData15(source.getImageRect(), new FileData(new byte[2])); assertFalse(artwork.matchesNative(gpu));
    gpu.uploadData15(source.getImageRect(), source.getImageData()); assertTrue(artwork.matchesNative(gpu));
    artwork.close(); artwork.close(); assertFalse(artwork.matchesNative(gpu));
  }

  private static void set(final Object owner, final String name, final Object value) throws Exception {
    final var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
  }

  private static Object get(final Object owner, final String name) throws Exception {
    final var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
  }

  @Test void ownersReleaseAllHdResourcesOnRepeatedTeardownWithoutReloading() throws Exception {
    final SmokeParticleEffect smoke = new SmokeParticleEffect();
    set(smoke, "smokeHd", owned(tim(465))); set(smoke, "particleHd", new MeshObj("smoke", new Mesh[0]));
    smoke.deallocate(); smoke.deallocate();
    assertNull(get(smoke, "smokeHd")); assertNull(get(smoke, "particleHd"));
    final AttachedSobjEffect trails = new AttachedSobjEffect();
    set(trails, "dustHd", owned(tim(465))); set(trails, "quadDustHd", new MeshObj("dust", new Mesh[0]));
    final var feet = (EffectArtwork[])get(trails, "footprintArtwork");
    feet[0] = owned(tim(465)); feet[1] = owned(tim(465));
    trails.deallocateAttachedSobjEffects(); trails.deallocateAttachedSobjEffects();
    assertNull(get(trails, "dustHd")); assertNull(get(trails, "quadDustHd")); assertNull(feet[0]); assertNull(feet[1]);
    // Populate only the constructor's state-type dependency, without booting gameplay.
    final var id = new org.legendofdragoon.modloader.registries.RegistryId("lod", "submap");
    final var registry = (legend.game.EngineStateTypeRegistry)legend.core.GameEngine.REGISTRIES.engineStateTypes;
    if(!registry.hasEntry(id)) registry.register(id, new legend.game.EngineStateType<>(SMap.class, SMap::new));
    final SMap map = new SMap();
    set(map, "savepointArtwork", owned(tim(465))); set(map, "savepointCircleHd", new MeshObj("glow", new Mesh[0]));
    final var release = SMap.class.getDeclaredMethod("releaseSavepointArtwork"); release.setAccessible(true);
    release.invoke(map); release.invoke(map);
    assertNull(get(map, "savepointArtwork")); assertNull(get(map, "savepointCircleHd"));
  }

  @Test void eventControlIsImmutableAndEarlierModSelectionHasPriority() throws Exception {
    final byte[] source = tim(465); final EffectTextureEvent event = new EffectTextureEvent("dust", source);
    source[4] = 99; final byte[] copy = event.source(); copy[4] = 98; assertEquals(8, event.source()[4]);
    final Image previous = new Image(new byte[4], 1, 1); event.replacement = previous;
    new FxHdMod().replace(event); assertSame(previous, event.replacement);
    assertThrows(IOException.class, () -> FxHdMod.read("dust", tim(465)));
    assertThrows(IOException.class, () -> FxHdMod.read("smoke_2", tim(465)));
  }
}
