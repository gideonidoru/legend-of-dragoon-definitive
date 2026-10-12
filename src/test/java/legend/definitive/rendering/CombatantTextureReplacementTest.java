package legend.definitive.rendering;

import legend.core.GameEngine;
import legend.core.gte.ModelPart10;
import legend.core.renderer.RenderEngine;
import legend.core.renderer.Texture;
import legend.core.renderer.noop.NoopApi;
import legend.game.EngineStateType;
import legend.game.EngineStateTypeRegistry;
import legend.game.EngineStates;
import legend.game.characters.StatTypeRegistry;
import legend.game.characters.StatTypeRegistryEvent;
import legend.game.combat.Battle;
import legend.game.combat.bent.BattleEntity27c;
import legend.game.combat.bent.BattleEntityTypeRegistry;
import legend.game.combat.bent.BattleEntityTypeRegistryEvent;
import legend.game.combat.bent.MonsterBattleEntity;
import legend.game.combat.types.CombatantStruct1a8;
import legend.game.modding.coremod.CoreMod;
import legend.game.saves.ConfigRegistry;
import legend.game.saves.ConfigRegistryEvent;
import legend.game.unpacker.FileData;
import legend.lodmod.LodMod;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.legendofdragoon.modloader.registries.RegistryId;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Actual TIM arrival and combatant render preparation; synthetic data, no native output. */
final class CombatantTextureReplacementTest {
  @TempDir Path temporary;

  @ParameterizedTest
  @ValueSource(strings = {"-1", "1", "-1First"})
  void replacementKeepsDelayedFallbackAndRefreshesImageAndPalette(final String scenario) throws Exception {
    final Path output = this.temporary.resolve("replacement-" + scenario + ".log");
    final Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
      "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
      CombatantTextureReplacementTest.class.getName(), scenario)
      .redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue(child.waitFor(15, TimeUnit.SECONDS), "Texture replacement must finish within its deadline");
      final String log = Files.readString(output);
      assertEquals(0, child.exitValue(), log);
      assertTrue(log.contains("PASS replacement"), log);
    } finally {
      if(child.isAlive()) child.destroyForcibly();
      assertTrue(child.waitFor(5, TimeUnit.SECONDS), "Replacement child must stop");
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
    final var battle = new Battle(); EngineStates.currentEngineState_8004dd04 = battle;
    final var actor = new MonsterBattleEntity(battle, "Texture replacement fixture", 0);
    final var combatant = new CombatantStruct1a8(); actor.combatant_144 = combatant;
    combatant.vramSlot_1a0 = Integer.parseInt(args[0].replace("First", "")); combatant._1a4 = combatant._1a6 = -1;
    // No geometry is needed to exercise the real private VRAM preparation boundary.
    actor.model_148.modelParts_00 = new ModelPart10[0]; actor.model_148.shadowType_cc = 0;
    final var render = BattleEntity27c.class.getDeclaredMethod("renderBttlModel", legend.game.types.Model124.class, CombatantStruct1a8.class);
    render.setAccessible(true);
    try {
      if(args[0].endsWith("First")) {
        render.invoke(actor, actor.model_148, combatant);
        assertNull(combatant.texture, "First model must wait for its TIM before allocating private VRAM");
      }
      for(int cycle = 0; cycle < 3; cycle++) {
        final int first = 42 + cycle, next = 84 + cycle;
        battle.loadCombatantTim(combatant, tim(first)); render.invoke(actor, actor.model_148, combatant);
        assertWords(combatant, first);
        final var fallbackGpu = combatant.texture; final var fallbackTim = combatant.tim;
        battle.deallocateCombatant(combatant); render.invoke(actor, actor.model_148, combatant);
        assertSame(fallbackGpu, combatant.texture, "Model-first reload must keep its GPU fallback");
        assertSame(fallbackTim, combatant.tim, "Delayed TIM must keep its source fallback");
        assertWords(combatant, first);
        battle.loadCombatantTim(combatant, new FileData(new byte[0]));
        render.invoke(actor, actor.model_148, combatant); assertWords(combatant, first);
        battle.loadCombatantTim(combatant, tim(next)); render.invoke(actor, actor.model_148, combatant);
        assertWords(combatant, next);
        render.invoke(actor, actor.model_148, combatant); assertWords(combatant, next);
      }
      System.out.println("PASS replacement " + args[0]);
    } finally {
      if(combatant.texture != null) combatant.texture.deleteVram();
      Texture.deleteTextures();
    }
  }

  private static void assertWords(final CombatantStruct1a8 combatant, final int value) {
    final var gpu = combatant.texture != null ? combatant.texture : GameEngine.GPU;
    final var rect = Battle.combatantTimRects_800fa6e0[1];
    for(int y = 0; y < rect.h; y++) for(int x = 0; x < rect.w; x++) {
      assertEquals(y >= 240 ? value + 100 : value, gpu.getPixel15(rect.x + x, rect.y + y),
        "Replacement image and overlaid palette must reach active VRAM at " + x + "," + y);
    }
  }

  private static FileData tim(final int value) {
    final ByteBuffer bytes = ByteBuffer.allocate(34848).order(ByteOrder.LITTLE_ENDIAN);
    bytes.putInt(0x10).putInt(8).putInt(2060);
    bytes.putShort((short)512).putShort((short)496).putShort((short)64).putShort((short)16);
    for(int i = 0; i < 1024; i++) bytes.putShort((short)(value + 100));
    bytes.putInt(32780).putShort((short)512).putShort((short)256).putShort((short)64).putShort((short)256);
    for(int i = 0; i < 16384; i++) bytes.putShort((short)value);
    return new FileData(bytes.array());
  }
}
