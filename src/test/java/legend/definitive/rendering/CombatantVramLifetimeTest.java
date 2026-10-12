package legend.definitive.rendering;

import legend.core.GameEngine;
import legend.core.gpu.Gpu;
import legend.core.renderer.RenderEngine;
import legend.core.renderer.Texture;
import legend.core.renderer.noop.NoopApi;
import legend.game.EngineStateType;
import legend.game.EngineStateTypeRegistry;
import legend.game.EngineStates;
import legend.game.combat.Battle;
import legend.game.combat.types.CombatantStruct1a8;
import legend.game.modding.coremod.CoreMod;
import legend.game.saves.ConfigRegistry;
import legend.game.saves.ConfigRegistryEvent;
import legend.game.tim.Tim;
import legend.game.unpacker.FileData;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.legendofdragoon.modloader.registries.RegistryId;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Shipping combatant deallocation with counted backends; no native graphics or game data. */
final class CombatantVramLifetimeTest {
  @TempDir Path temporary;

  @ParameterizedTest
  @ValueSource(strings = {"remove", "removeDetail", "clear", "clearDetail", "destroy", "destroyDetail"})
  void finalRetirementPreservesReloadAndReleasesOwnedVram(final String scenario) throws Exception {
    final Path output = this.temporary.resolve("retirement-" + scenario + ".log");
    final Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
      "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
      CombatantVramLifetimeTest.class.getName(), scenario)
      .redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue(child.waitFor(15, TimeUnit.SECONDS), "Combatant retirement must finish within its deadline");
      final String log = Files.readString(output);
      assertEquals(0, child.exitValue(), log);
      assertTrue(log.contains("PASS retirement"), log);
    } finally {
      if(child.isAlive()) child.destroyForcibly();
      assertTrue(child.waitFor(5, TimeUnit.SECONDS), "Retirement child must stop");
    }
  }

  public static void main(final String[] args) throws Exception {
    CoreMod.registerConfig(new ConfigRegistryEvent((ConfigRegistry)GameEngine.REGISTRIES.config));
    ((EngineStateTypeRegistry)GameEngine.REGISTRIES.engineStateTypes).register(new RegistryId("lod", "battle"), new EngineStateType<>(Battle.class, Battle::new));
    final var api = RenderEngine.class.getDeclaredField("api"); api.setAccessible(true); api.set(GameEngine.RENDERER, new NoopApi());
    final var battle = new Battle(); EngineStates.currentEngineState_8004dd04 = battle;
    // Scene destruction stops music; install a sequencer without opening native audio output.
    final var sequencerField = legend.core.audio.AudioThread.class.getDeclaredField("sequencer"); sequencerField.setAccessible(true);
    sequencerField.set(GameEngine.AUDIO_THREAD, new legend.core.audio.sequencer.Sequencer(true, 24,
      legend.core.audio.InterpolationPrecision.Double, legend.core.audio.PitchResolution.Quadruple,
      legend.core.audio.EffectsOverTimeGranularity.Double));
    final var combatantsField = Battle.class.getDeclaredField("combatants_8005e398"); combatantsField.setAccessible(true);
    final var countField = Battle.class.getDeclaredField("combatantCount_800c66a0"); countField.setAccessible(true);
    final var combatants = (CombatantStruct1a8[])combatantsField.get(battle);
    final var neighbour = new RendererResourceLifetimeTest.CountedTexture();
    neighbour.persistent = true;
    GameEngine.GPU.vramTexture15 = neighbour;
    try {
      for(int cycle = 0; cycle < 3; cycle++) {
        final var combatant = new CombatantStruct1a8();
        combatant._1a4 = combatant._1a6 = -1;
        final Tim retainedTim = new Tim(new FileData(new byte[22])); combatant.tim = retainedTim;
        combatants[9] = combatant; countField.setInt(battle, 1);
        final Gpu ownedGpu = new Gpu(); combatant.texture = ownedGpu;
        final var owned = new RendererResourceLifetimeTest.CountedTexture(); owned.persistent = true;
        ownedGpu.vramTexture15 = owned;
        final var detail = args[0].endsWith("Detail") ? new RendererResourceLifetimeTest.CountedTexture() : null;
        final var colour = args[0].endsWith("Detail") ? new RendererResourceLifetimeTest.CountedTexture() : null;
        final var detailField = Gpu.class.getDeclaredField("effectDetailTexture"); detailField.setAccessible(true);
        final var colourField = Gpu.class.getDeclaredField("vramTexture24"); colourField.setAccessible(true);
        if(detail != null) { detail.persistent = true; detailField.set(ownedGpu, detail); }
        if(colour != null) { colour.persistent = true; colourField.set(ownedGpu, colour); }
        try {
          battle.deallocateCombatant(combatant);
          Texture.deleteTextures();
          assertSame(ownedGpu, combatant.texture, "Model-first reload must keep its private VRAM fallback");
          assertSame(retainedTim, combatant.tim, "Delayed texture replacement must keep a usable TIM");
          assertEquals(0, owned.releases, "Temporary model deallocation must keep private VRAM alive");
          if(args[0].startsWith("remove")) battle.removeCombatant(9);
          else if(args[0].startsWith("clear")) battle.clearCombatants();
          else battle.destroy();
          assertNull(combatant.tim, "Final retirement must release its TIM source");
          assertNull(combatant.texture, "Dead combatant must release its private GPU owner");
          assertNull(ownedGpu.vramTexture15, "Private VRAM must leave its retired owner");
          assertNull(colourField.get(ownedGpu), "Optional colour VRAM must leave its retired owner");
          assertFalse(ownedGpu.hasEffectArtwork(), "Private FX detail must leave its retired owner");
          assertEquals(0, owned.releases, "Backend retirement remains deferred");
          if(detail != null) assertEquals(0, detail.releases);
          if(colour != null) assertEquals(0, colour.releases);
          battle.deallocateCombatant(combatant);
          Texture.deleteTextures(); Texture.deleteTextures();
          assertEquals(1, owned.releases, "Repeated combatant cleanup must retire VRAM exactly once");
          if(detail != null) assertEquals(1, detail.releases, "FX detail must retire with its VRAM owner");
          if(colour != null) assertEquals(1, colour.releases, "Optional colour VRAM must retire with its owner");
          assertEquals(0, neighbour.releases, "Global VRAM must remain alive");
          assertSame(neighbour, GameEngine.GPU.vramTexture15);
        } finally {
          owned.delete(); if(detail != null) detail.delete(); if(colour != null) colour.delete(); Texture.deleteTextures();
        }
      }
      System.out.println("PASS retirement " + args[0]);
    } finally {
      neighbour.delete(); Texture.deleteTextures();
    }
  }
}
