package legend.definitive.mods;

import legend.core.GameEngine;
import legend.core.Version;
import legend.game.inventory.screens.CampaignSelectionScreen;
import legend.game.modding.coremod.CoreMod;
import legend.game.saves.ConfigCollection;
import legend.game.types.MessageBoxResult;
import org.legendofdragoon.modloader.ModManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Actual discovery, event and registry boot with packaged modules in an isolated headless JVM. */
public final class ManagedModBootProbe {
  private static void require(final boolean condition, final String message) {
    if(!condition) throw new AssertionError(message);
  }

  private static Set<String> loaded() {
    final Set<String> result = new HashSet<>();
    for(final String id : GameEngine.MODS.getAllModIds()) if(GameEngine.MODS.isReady(id)) result.add(id);
    return Set.copyOf(result);
  }

  private static void boot(final Set<String> requested, final Set<String> expected) {
    require(GameEngine.bootVisibleMods(requested).isEmpty(), "No missing requested mod IDs");
    require(GameEngine.MODS.getFailedToLoad().isEmpty(), "No failed module constructors");
    require(loaded().equals(expected), "Expected " + expected + ", loaded " + loaded());
  }

  public static void main(final String[] args) throws Exception {
    require(Boolean.getBoolean("java.awt.headless"), "This probe requires headless mode");
    final Path preferences = Path.of(args[1]).toRealPath().resolve("definitive-hd-mods.properties");
    require(!Files.exists(preferences), "Probe needs isolated preferences");
    System.setProperty(ManagedModProfile.PROFILE_PROPERTY, "hd");
    System.setProperty(ManagedModProfile.PREFERENCES_PROPERTY, preferences.toString());

    final var accessField = GameEngine.class.getDeclaredField("MOD_ACCESS");
    accessField.setAccessible(true);
    ((ModManager.Access)accessField.get(null)).findMods(Path.of(args[0]), Version.VERSION);
    require(GameEngine.MODS.getAllModIds().containsAll(ManagedModProfile.MOD_IDS), "All seven real module JARs discovered");
    require(GameEngine.MODS.getWrongVersions().isEmpty(), "All JAR versions accepted by the actual loader");
    final Set<String> required = Set.of("lod", "lod_core");
    final Set<String> hd = new HashSet<>(required);
    hd.addAll(ManagedModProfile.MOD_IDS);
    final Set<String> legacy = new HashSet<>(Set.of("lod"));
    boot(legacy, hd);
    require(legacy.equals(Set.of("lod")) && !Files.exists(preferences), "Default boot never rewrites campaign or preferences");
    System.out.println("PASS: legacy campaign boots all seven packaged HD modules and required core mods");
    final Set<String> withCustom = new HashSet<>(hd);
    withCustom.add("turn_order");
    boot(Set.of("lod", "turn_order"), withCustom);
    System.out.println("PASS: managed defaults preserve an explicitly selected custom gameplay module");
    checkCampaignWithoutSelection();

    System.setProperty(ManagedModProfile.PROFILE_PROPERTY, "original");
    final Set<String> original = new HashSet<>(hd);
    original.removeAll(Set.of("scbackgroundhd", "modelshd"));
    boot(legacy, original);
    System.out.println("PASS: original artwork profile boots five HD modules and excludes Skurfa/ModelsHD");

    System.setProperty(ManagedModProfile.PROFILE_PROPERTY, "hd");
    ManagedModProfile.recordSelections(Map.of("uihd", false, "fmvhd", false));
    final byte[] saved = Files.readAllBytes(preferences);
    final Set<String> optedOut = new HashSet<>(hd);
    optedOut.removeAll(Set.of("uihd", "fmvhd"));
    boot(legacy, optedOut);
    require(java.util.Arrays.equals(saved, Files.readAllBytes(preferences)), "Boot cannot rewrite accepted opt-outs");
    require(GameEngine.bootVisibleMods(legacy, Map.of("uihd", true)).isEmpty(), "Staged preview loads without missing mods");
    final Set<String> preview = new HashSet<>(optedOut);
    preview.add("uihd");
    require(loaded().equals(preview), "Actual staged preview loads UIHD");
    require(java.util.Arrays.equals(saved, Files.readAllBytes(preferences)), "Preview cannot persist staged choices");
    System.out.println("PASS: actual loader honors durable opt-outs and temporary preview re-enable");
    System.setProperty(ManagedModProfile.PROFILE_PROPERTY, "original");
    final Set<String> originalOptedOut = new HashSet<>(original);
    originalOptedOut.removeAll(Set.of("uihd", "fmvhd"));
    boot(legacy, originalOptedOut);
    require(java.util.Arrays.equals(saved, Files.readAllBytes(preferences)), "Changing artwork profile cannot rewrite visual preferences");
    System.setProperty(ManagedModProfile.PROFILE_PROPERTY, "hd");

    require(GameEngine.bootMods(Set.of()).isEmpty(), "Raw conversion boot has no missing mods");
    require(loaded().equals(required), "Raw conversion boot remains required-core-only even in managed HD mode");
    System.clearProperty(ManagedModProfile.PREFERENCES_PROPERTY);
    boot(legacy, required);
    require(GameEngine.bootVisibleMods(Set.of("lod", "custom_missing")).equals(Set.of("custom_missing")), "Upstream custom missing IDs reach the normal loader");
    require(loaded().equals(required), "Unmanaged load never injects visual modules");
    System.out.println("PASS: raw conversion and unmanaged visible boot retain upstream selection behavior");
  }

  private static void checkCampaignWithoutSelection() throws Exception {
    final ConfigCollection config = new ConfigCollection(false);
    final var entry = CoreMod.ENABLED_MODS_CONFIG.get();
    final var initial = CampaignSelectionScreen.class.getDeclaredMethod("campaignModIds", ConfigCollection.class, Set.class);
    initial.setAccessible(true);
    @SuppressWarnings("unchecked") final Set<String> original = (Set<String>)initial.invoke(null, config, GameEngine.MODS.getAllModIds());
    require(original.contains("turn_order"), "Absent-key campaign stages its already-active custom module");
    require(!config.hasConfig(entry), "Opening a legacy campaign must preserve its missing enabled_mods key");
    final var confirm = CampaignSelectionScreen.class.getDeclaredMethod("confirmModSelection", StagedModSelection.class, MessageBoxResult.class, Runnable.class, Runnable.class);
    confirm.setAccessible(true);
    final Set<String> cancelledIds = new HashSet<>(original);
    final var cancelled = new StagedModSelection(cancelledIds, GameEngine.MODS.getAllModIds());
    require(cancelled.isEnabled("turn_order"), "Absent-key custom checkbox is enabled");
    cancelled.setEnabled("uihd", false);
    cancelled.setEnabled("turn_order", false);
    confirm.invoke(null, cancelled, MessageBoxResult.NO, (Runnable)() -> { throw new AssertionError("NO cannot save a legacy campaign"); }, (Runnable)() -> {});
    require(!config.hasConfig(entry), "NO leaves absent campaign key untouched");

    final Set<String> acceptedIds = new HashSet<>(original);
    final var accepted = new StagedModSelection(acceptedIds, GameEngine.MODS.getAllModIds());
    accepted.setEnabled("uihd", false);
    confirm.invoke(null, accepted, MessageBoxResult.YES, (Runnable)() -> config.setConfig(entry, acceptedIds.toArray(String[]::new)), (Runnable)() -> {});
    require(Set.of(config.getConfig(entry)).contains("turn_order"), "YES must preserve previously-active custom modules");
    ManagedModProfile.recordSelection("uihd", true); // Restore initial defaults for following cases.
    config.setConfig(entry, new String[0]);
    require(((Set<?>)initial.invoke(null, config, GameEngine.MODS.getAllModIds())).isEmpty(), "Explicitly empty selection differs from an absent legacy key");
    System.out.println("PASS: actual registered legacy campaign config preserves custom mods through display, NO and accepted HD edit");
  }
}
