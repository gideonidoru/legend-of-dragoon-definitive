// Original synthetic material fixtures (2026-10-09), AGPL v3; no retail assets.
package legend.definitive.materials;

import com.google.gson.Gson;
import legend.definitive.textures.TexturePilot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static legend.definitive.materials.MaterialAtlasFixture.read;
import static legend.definitive.materials.MaterialAtlasFixture.save;
import static legend.definitive.materials.MaterialAtlasFixture.model;
import static legend.definitive.materials.MaterialAtlasFixture.tim;

class MaterialAtlasTest {
  @TempDir Path temporary;
  private MaterialAtlasFixture.Fixture fixture(final int scale) throws Exception {
    return MaterialAtlasFixture.create(this.temporary, scale);
  }
  private static void pixel(final MaterialAtlasFixture.Fixture fixture, final int x, final int y, final int colour) throws IOException {
    final Path png = fixture.folder().resolve("atlas-engine-stp.png");
    final var image = ImageIO.read(png.toFile()); image.setRGB(x, y, colour); ImageIO.write(image, "png", png.toFile());
    fixture.manifest().addProperty("atlasEngineSha256", TexturePilot.sha256(Files.readAllBytes(png))); save(fixture);
  }
  @Test void separatesConflictingPalettesAndExposesImmutableValidatedPair() throws Exception {
    for(final int scale : new int[]{2, 4}) {
      final MaterialAtlas atlas = read(fixture(scale));
      assertTrue(atlas.rgba().isDirect(), "Native texture upload requires direct pixel storage");
      assertEquals(2, atlas.regions().size()); assertEquals(scale, atlas.scale());
      assertEquals(2L * 20 * 20 * scale * scale, atlas.checkedTexels());
      for(final var region : atlas.regions()) {
        final int offset = ((region.y() + 8 * scale) * atlas.width() + region.x() + 9 * scale) * 4;
        final ByteBuffer rgba = atlas.rgba();
        assertEquals(region.palette() == 0 ? 255 : 0, Byte.toUnsignedInt(rgba.get(offset)));
        assertEquals(region.palette() == 1 ? 255 : 0, Byte.toUnsignedInt(rgba.get(offset + 2)));
        assertEquals(0, rgba.get(offset + 3));
      }
      assertThrows(ReadOnlyBufferException.class, () -> atlas.rgba().put((byte)1));
      atlas.rgba().position(4); assertEquals(0, atlas.rgba().position());
      assertThrows(UnsupportedOperationException.class, () -> atlas.regions().clear());
    }
  }
  @Test void rejectsChangedIdentityIncompleteMapsAndFractionalScale() throws Exception {
    final var fixture = fixture(2);
    assertThrows(IOException.class, () -> MaterialAtlas.read(fixture.folder(), new byte[40], fixture.tim()));
    fixture.manifest().getAsJsonArray("materials").remove(0); save(fixture); assertThrows(IOException.class, () -> read(fixture));
    final var moved = fixture(2); moved.manifest().getAsJsonArray("materials").get(0).getAsJsonObject().getAsJsonArray("atlasRect").set(0, new com.google.gson.JsonPrimitive(1)); save(moved); assertThrows(IOException.class, () -> read(moved));
    final var fractional = fixture(2); fractional.manifest().addProperty("scale", 2.0); save(fractional); assertThrows(IOException.class, () -> read(fractional));
  }
  @Test void checksDiscardVisibleBlackStpAndPreservedPixelsEvenWithNewHash() throws Exception {
    for(final int[] change : new int[][]{{0, 0x00ff0000}, {1, 0xffff0000}, {2, 0xff000001}, {3, 0x0000ff00}, {1, 0x0000ff00}}) {
      final var fixture = fixture(2); final var region = SourceMaterials.layout(fixture.model(), fixture.tim(), 2).regions().get(0);
      pixel(fixture, region.x() + (8 + change[0]) * 2, region.y() + 16, change[1]);
      assertThrows(IOException.class, () -> read(fixture));
    }
  }
  @Test void acceptsPinnedNeuralDescriptionButStillGuardsOriginalRegions() throws Exception {
    final var fixture = fixture(2);
    fixture.manifest().addProperty("algorithm", "neural"); fixture.manifest().addProperty("strength", 0.5);
    fixture.manifest().addProperty("weightsSha256", "548a36f9c3f4ab8da56cd3b13badf23968bee207b396dad14d04b830e5f2ab2d");
    fixture.manifest().addProperty("paramsSha256", "b88ff4f00ebf019a7fdac17fdd45a7fd3665d37509efc5baf2e4da2e24420a04"); save(fixture);
    final var region = SourceMaterials.layout(fixture.model(), fixture.tim(), 2).regions().get(0);
    pixel(fixture, region.x() + 18, region.y() + 16, 0x0000ff00); assertDoesNotThrow(() -> read(fixture));
    fixture.manifest().add("preservedPalettes", new Gson().toJsonTree(List.of(0))); save(fixture); assertThrows(IOException.class, () -> read(fixture));
    fixture.manifest().addProperty("weightsSha256", "unrecognized"); save(fixture); assertThrows(IOException.class, () -> read(fixture));
  }
  @Test void rejectsLinkedFilesDuplicateKeysOversizedDimensionsAndHiddenGapPixels() throws Exception {
    final var linked = fixture(2); final Path manifest = linked.folder().resolve("manifest.json"), target = linked.folder().resolve("other.json");
    Files.move(manifest, target); Files.createSymbolicLink(manifest, target); assertThrows(IOException.class, () -> read(linked));
    final var duplicate = fixture(2); final String json = duplicate.manifest().toString(); Files.writeString(duplicate.folder().resolve("manifest.json"), "{\"scale\":2," + json.substring(1)); assertThrows(IOException.class, () -> read(duplicate));
    final var dimensions = fixture(2); final Path png = dimensions.folder().resolve("atlas-engine-stp.png"); final byte[] bytes = Files.readAllBytes(png);
    ByteBuffer.wrap(bytes).putInt(16, 4097); Files.write(png, bytes); dimensions.manifest().addProperty("atlasEngineSha256", TexturePilot.sha256(bytes)); save(dimensions); assertThrows(IOException.class, () -> read(dimensions));
    final var gap = fixture(2); pixel(gap, 50, 70, 0x00ff0000); assertThrows(IOException.class, () -> read(gap));
  }
  @Test void refusesUnsupportedSourceFamiliesAndMalformedPackets() throws Exception {
    for(final int[] change : new int[][]{{4, 1}, {8, 1}, {16, 1}, {85, 255}, {90, 2}, {94, 0x80}, {96, 0x80}}) {
      final byte[] source = model(); source[change[0]] = (byte)change[1]; assertThrows(IOException.class, () -> SourceMaterials.layout(source, tim(), 2));
    }
    final byte[] texture = tim(); texture[4] = 9; assertThrows(IOException.class, () -> SourceMaterials.layout(model(), texture, 2));
    final byte[] padded = java.util.Arrays.copyOf(tim(), tim().length + 64); assertThrows(IOException.class, () -> SourceMaterials.layout(model(), padded, 2));
    assertThrows(IOException.class, () -> SourceMaterials.layout(new byte[12], tim(), 2));
  }
  @Test void boundsManifestSizeNestingNumbersAndRegularFiles() throws Exception {
    for(final String json : List.of("[".repeat(18) + "0" + "]".repeat(18), "{\"scale\":1e999}", "{\"scale\":9223372036854775808}", " ".repeat(65537), "{\"scale\":true}")) {
      final var fixture = fixture(2); Files.writeString(fixture.folder().resolve("manifest.json"), json);
      assertThrows(IOException.class, () -> read(fixture));
    }
    final var directory = fixture(2); Files.delete(directory.folder().resolve("manifest.json")); Files.createDirectory(directory.folder().resolve("manifest.json"));
    assertThrows(IOException.class, () -> read(directory));
  }
  @Test void authoredProvenanceDoesNotBypassSourceCoverageValidation() throws Exception {
    final var fixture = fixture(2);
    fixture.manifest().addProperty("algorithm", "authored"); save(fixture);
    assertThrows(IOException.class, () -> read(fixture));
    fixture.manifest().add("authoring", new Gson().toJsonTree(Map.of("baseAtlasSha256", "a".repeat(64), "generatedImageSha256", "b".repeat(64), "promptSha256", "c".repeat(64)))); save(fixture);
    assertNotNull(read(fixture));
    pixel(fixture, 0, 0, 0x00ff0000);
    assertThrows(IOException.class, () -> read(fixture));
  }

  @Test void characterUvRestoresNativeRelocationWithoutCombiningConflictingPalettes() throws Exception {
    final var atlas = read(fixture(4));
    final var nativeUv = new legend.game.tmd.UvAdjustmentMetrics14(1, 320, 256);
    final var map = new CharacterUv(atlas, 2, nativeUv);
    final float[] first = map.map(0, 1.5f, 2.5f), second = map.map(1, 1.5f, 2.5f);
    assertTrue(first[0] != second[0] || first[1] != second[1]);
    final var shifted = new CharacterUv(atlas, 2, new legend.game.tmd.UvAdjustmentMetrics14(1, 0, 240, 16, 254));
    assertThrows(IllegalArgumentException.class, () -> new CharacterUv(atlas, 2, new legend.game.tmd.UvAdjustmentMetrics14(1, 16, 254)));
    assertArrayEquals(first, shifted.map(0, 65.5f, 0.5f), 0.000001f);
    assertThrows(IllegalArgumentException.class, () -> map.map(3, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> map.map(0, 20, 0));
    assertThrows(IllegalArgumentException.class, () -> map.map(0, Float.NaN, 0));
  }

  @Test void characterSurfacesAreExplicitAndIsolatedFromOtherPalettes() throws Exception {
    final var atlas = read(fixture(2));
    final var metal = new legend.core.renderer.SurfaceResponse(legend.core.renderer.SurfaceMaterial.METAL, 0.4f);
    final var metadata = new java.util.HashMap<Integer, legend.core.renderer.SurfaceResponse>();
    metadata.put(1, metal);
    final var map = new CharacterUv(atlas, 2, legend.game.tmd.UvAdjustmentMetrics14.NONE, metadata);
    metadata.clear();
    assertSame(metal, map.surface(1));
    assertNull(map.surface(0));
  }

  @Test void mappedMeshesKeepNativeGeometryPacketsAndTransparency() throws Exception {
    final var renderer = legend.core.GameEngine.RENDERER.getClass().getDeclaredField("api");
    renderer.setAccessible(true);
    final var previous = renderer.get(legend.core.GameEngine.RENDERER);
    renderer.set(legend.core.GameEngine.RENDERER, new legend.core.renderer.noop.NoopApi());
    final var accessField = legend.core.GameEngine.class.getDeclaredField("EVENT_ACCESS");
    accessField.setAccessible(true);
    final var access = (org.legendofdragoon.modloader.events.EventManager.Access)accessField.get(null);
    access.initialize(legend.core.GameEngine.MODS);
    try {
      final var input = fixture(4);
      final var atlas = read(input);
      final var model = new legend.game.types.CContainer("synthetic mapped palettes", new legend.game.unpacker.FileData(input.model().clone())).tmdPtr_00.tmd;
      final var part = model.objTable[0];
      final byte[] packet = part.primitives_10[0].data()[0].clone();
      final var nativeMesh = legend.game.tmd.TmdObjLoader.fromObjTable("native control", part);
      final var mapped = legend.game.tmd.TmdObjLoader.fromObjTableMapped("mapped control", part,
        new CharacterUv(atlas, 2, legend.game.tmd.UvAdjustmentMetrics14.NONE));
      for(int mesh = 0; mesh < nativeMesh.meshes.length; mesh++) {
        final float[] original = nativeMesh.meshes[mesh].vertices(), enhanced = mapped.meshes[mesh].vertices();
        assertEquals(original.length, enhanced.length);
        assertEquals(nativeMesh.meshes[mesh].translucencyMode(), mapped.meshes[mesh].translucencyMode());
        for(int row = 0; row < original.length; row += 16) {
          for(int field = 0; field < 7; field++) assertEquals(original[row + field], enhanced[row + field]);
          assertTrue(enhanced[row + 7] >= 0 && enhanced[row + 7] <= 1);
          assertTrue(enhanced[row + 8] >= 0 && enhanced[row + 8] <= 1);
          assertEquals(3, (int)enhanced[row + 9] >>> 7 & 3);
          for(int field = 10; field < 16; field++) assertEquals(original[row + field], enhanced[row + field]);
        }
      }
      assertArrayEquals(packet, part.primitives_10[0].data()[0]);
      nativeMesh.delete();
      mapped.delete();
    } finally {
      access.reset();
      renderer.set(legend.core.GameEngine.RENDERER, previous);
    }
  }

}
