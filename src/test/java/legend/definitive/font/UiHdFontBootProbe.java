package legend.definitive.font;

import legend.core.Async;
import legend.core.GameEngine;
import legend.core.Version;
import legend.core.font.EffectiveFont;
import legend.core.font.Font;
import legend.core.font.PackagedFont;
import legend.game.modding.coremod.CoreMod;
import org.legendofdragoon.modloader.ModManager;
import java.nio.file.Path;
import java.util.Set;

/** Actual packaged discovery and boot whitelist; CPU selection only, no window or GPU initialization. */
public final class UiHdFontBootProbe {
  private static Object field(final Object instance, final Class<?> type, final String name) throws Exception {
    final var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(instance);
  }
  private static void require(final boolean condition, final String message) {
    if(!condition) throw new AssertionError(message);
  }
  private static void boot(final boolean enabled) {
    require(GameEngine.bootMods(enabled ? Set.of("lod", "uihd") : Set.of("lod")).isEmpty(), "No missing mod IDs");
    require(GameEngine.MODS.getFailedToLoad().isEmpty() && GameEngine.MODS.isReady("uihd") == enabled, "Actual loader activation matches whitelist");
  }
  public static void main(final String[] args) throws Exception {
    require(Boolean.getBoolean("java.awt.headless"), "Headless mode required");
    try {
      ((ModManager.Access)field(null, GameEngine.class, "MOD_ACCESS")).findMods(Path.of(args[0]), Version.VERSION);
      final Font captured = GameEngine.DEFAULT_FONT;
      final EffectiveFont effective = (EffectiveFont)captured;
      boot(false);
      require(field(effective, EffectiveFont.class, "owned") == null && GameEngine.DEFAULT_FONT == captured, "Initially disabled UIHD retains configured captured handle");
      boot(true);
      final String saved = GameEngine.CONFIG.getConfig(CoreMod.RETAIL_FONT_CONFIG.get());
      final Font configured = GameEngine.FONTS.get(Path.of("gfx/fonts/smooth.json"));
      GameEngine.configureDefaultFont(configured);
      final Object first = field(effective, EffectiveFont.class, "owned");
      require(first instanceof PackagedFont, "Actual packaged UIHD listener selected validated artwork");
      require(field(first, Font.class, "texture") == null && field(first, Font.class, "obj") == null, "Selection has no GPU allocation");
      boot(false);
      require(field(effective, EffectiveFont.class, "owned") == null && effective.configured() == configured, "Whitelist unload restores configured font");
      boot(true);
      final Object second = field(effective, EffectiveFont.class, "owned");
      require(second instanceof PackagedFont && second != first, "Re-enable gets fresh packaged ownership");
      require(GameEngine.DEFAULT_FONT == captured && effective.configured() == configured, "Captured handle and configured choice remain stable");
      require(saved.equals(GameEngine.CONFIG.getConfig(CoreMod.RETAIL_FONT_CONFIG.get())), "Boot never overwrites saved font choice");
      boot(false);
      System.out.println("PASS: actual packaged mod discovery/boot enable-disable-reenable; stable Font descriptor, configured choice preserved; CPU selection only");
    } finally { Async.shutdown(); }
  }
}
