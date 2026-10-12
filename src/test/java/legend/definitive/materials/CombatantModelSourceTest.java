package legend.definitive.materials;

import legend.core.GameEngine;
import legend.core.renderer.Obj;
import legend.core.renderer.RenderEngine;
import legend.core.renderer.noop.NoopApi;
import legend.game.EngineStateType;
import legend.game.EngineStateTypeRegistry;
import legend.game.EngineStates;
import legend.game.characters.StatTypeRegistry;
import legend.game.characters.StatTypeRegistryEvent;
import legend.game.combat.Battle;
import legend.game.combat.bent.BattleEntityTypeRegistry;
import legend.game.combat.bent.BattleEntityTypeRegistryEvent;
import legend.game.combat.bent.MonsterBattleEntity;
import legend.game.combat.types.CombatantAsset0c;
import legend.game.combat.types.CombatantStruct1a8;
import legend.game.modding.coremod.CoreMod;
import legend.game.modding.events.battle.CombatantMaterialEvent;
import legend.game.saves.ConfigRegistry;
import legend.game.saves.ConfigRegistryEvent;
import legend.game.types.TmdAnimationFile;
import legend.game.unpacker.FileData;
import legend.lodmod.LodMod;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.legendofdragoon.modloader.events.EventListener;
import org.legendofdragoon.modloader.registries.RegistryId;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static legend.game.Scus94491BpeSegment_8006.battleState_8006e398;
import static org.junit.jupiter.api.Assertions.*;

/** Actual combatant parser/material callbacks, synthetic data and no native renderer. */
final class CombatantModelSourceTest {
  @TempDir Path temporary;

  @ParameterizedTest
  @ValueSource(strings = {"directory", "global", "directorySlice", "globalSlice"})
  void repeatedLoadsKeepCanonicalMaterialSource(final String scenario) throws Exception {
    final Path output = this.temporary.resolve(scenario + ".log");
    final Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
      "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
      CombatantModelSourceTest.class.getName(), scenario).redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue(child.waitFor(15, TimeUnit.SECONDS), "Source replay must finish within its deadline");
      final String log = Files.readString(output);
      assertEquals(0, child.exitValue(), log);
      assertTrue(log.contains("PASS " + scenario), log);
    } finally {
      if(child.isAlive()) child.destroyForcibly();
      assertTrue(child.waitFor(5, TimeUnit.SECONDS), "Source replay process must retire");
    }
  }

  public static final class Materials {
    final List<byte[]> sources = new ArrayList<>();
    @EventListener public void capture(final CombatantMaterialEvent event) {
      this.sources.add(event.modelSource.clone());
    }
  }

  public static void main(final String[] args) throws Exception {
    final var access = GameEngine.class.getDeclaredField("EVENT_ACCESS"); access.setAccessible(true);
    ((org.legendofdragoon.modloader.events.EventManager.Access)access.get(null)).initialize(GameEngine.MODS);
    CoreMod.registerConfig(new ConfigRegistryEvent((ConfigRegistry)GameEngine.REGISTRIES.config));
    LodMod.registerStatTypes(new StatTypeRegistryEvent((StatTypeRegistry)GameEngine.REGISTRIES.statTypes));
    LodMod.registerBentTypes(new BattleEntityTypeRegistryEvent((BattleEntityTypeRegistry)GameEngine.REGISTRIES.battleEntityTypes));
    ((EngineStateTypeRegistry)GameEngine.REGISTRIES.engineStateTypes).register(new RegistryId("lod", "battle"), new EngineStateType<>(Battle.class, Battle::new));
    final var api = RenderEngine.class.getDeclaredField("api"); api.setAccessible(true); api.set(GameEngine.RENDERER, new NoopApi());
    final var battle = new Battle(); EngineStates.currentEngineState_8004dd04 = battle; battle.FUN_800c8624();
    battle.currentEnemyNames_800c69d0[0] = "Synthetic source fixture";
    final var bent = new MonsterBattleEntity(battle, "Synthetic source fixture", 0); bent.typeBentSlot_276 = 0;
    final var combatant = new CombatantStruct1a8(); bent.combatant_144 = combatant;
    final byte[] original = model();
    final FileData cached;
    if(args[0].endsWith("Slice")) {
      final byte[] padded = new byte[original.length + 6];
      System.arraycopy(original, 0, padded, 3, original.length);
      cached = new FileData(padded, 3, original.length);
    } else {
      cached = new FileData(original.clone());
    }
    combatant._1a4 = -1;
    if(args[0].startsWith("global")) {
      combatant._1a4 = 0; battleState_8006e398.getGlobalAsset(0).data_00 = cached;
    } else {
      combatant.mrg_00 = new ArrayList<>();
      for(int i = 0; i < 33; i++) combatant.mrg_00.add(new FileData(new byte[0]));
      combatant.mrg_00.set(32, cached);
    }
    final ByteBuffer animation = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN);
    animation.putShort(12, (short)1).putShort(14, (short)2);
    combatant.assets_14[0] = new CombatantAsset0c.AnimType(new TmdAnimationFile(new FileData(animation.array())));
    // Texture arrival is independent of model parsing; this check captures the source sent to material providers.
    combatant.materials.textureReady(new byte[]{1});
    final var materials = new Materials(); GameEngine.EVENTS.register(materials);
    try {
      for(int reload = 0; reload < 3; reload++) {
        Battle.loadCombatantModelAndAnimation(battle, bent, combatant);
        Battle.applyCombatantMaterials(battle, bent.model_148, combatant);
        assertEquals(1, bent.model_148.modelParts_00[0].tmd_08.primitives_10[0].header() & 0xffff,
          "Native packet normalization must still occur on the parsed model");
      }
      assertEquals(3, materials.sources.size(), "Every model replacement must reach material selection");
      for(final byte[] selectedSource : materials.sources) assertArrayEquals(original, selectedSource,
        "Repeated combatant loading changed the identity used to select HD materials");
      assertArrayEquals(original, cached.getBytes(), "Parsing must preserve reusable cached model bytes");
      System.out.println("PASS " + args[0]);
    } finally {
      bent.model_148.deleteModelParts(); Obj.deleteObjects();
    }
  }

  private static byte[] model() {
    final ByteBuffer b = ByteBuffer.allocate(100).order(ByteOrder.LITTLE_ENDIAN);
    b.putInt(0, 12).putInt(12, 0x41).putInt(20, 1);
    b.putInt(24, 28).putInt(28, 3).putInt(32, 52).putInt(36, 1).putInt(40, 60).putInt(44, 1);
    b.putShort(52, (short)-1).putShort(54, (short)-1);
    b.putShort(60, (short)1).putShort(62, (short)-1);
    b.putShort(68, (short)0).putShort(70, (short)1);
    b.putShort(80, (short)4096);
    // Retail packet counts are normalized during parsing: the source deliberately starts with count zero.
    b.putInt(84, 0x2000_0000).putInt(88, 0x0080_8080);
    b.putShort(94, (short)0).putShort(96, (short)1).putShort(98, (short)2);
    return b.array();
  }
}
