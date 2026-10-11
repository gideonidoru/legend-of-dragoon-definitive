package legend.definitive.mods;

import legend.game.inventory.screens.CampaignSelectionScreen;
import legend.game.inventory.screens.ModsScreen;
import legend.game.inventory.screens.MenuScreen;
import legend.game.inventory.screens.MenuStack;
import legend.game.inventory.screens.MessageBoxScreen;
import legend.game.inventory.screens.controls.Checkbox;
import legend.game.types.MessageBoxResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Real checkbox callbacks and the existing-campaign confirmation seam, without a window. */
final class ManagedModSelectionTest {
  @TempDir Path temporary;
  private String oldProfile;
  private String oldPreferences;
  private Path preferences;

  @BeforeEach void managedProfile() throws IOException {
    this.oldProfile = System.getProperty(ManagedModProfile.PROFILE_PROPERTY);
    this.oldPreferences = System.getProperty(ManagedModProfile.PREFERENCES_PROPERTY);
    this.preferences = this.temporary.toRealPath().resolve("definitive-hd-mods.properties");
    System.setProperty(ManagedModProfile.PROFILE_PROPERTY, "hd");
    System.setProperty(ManagedModProfile.PREFERENCES_PROPERTY, this.preferences.toString());
  }

  @AfterEach void restore() {
    restore(ManagedModProfile.PROFILE_PROPERTY, this.oldProfile);
    restore(ManagedModProfile.PREFERENCES_PROPERTY, this.oldPreferences);
  }

  private static void restore(final String key, final String value) {
    if(value == null) System.clearProperty(key);
    else System.setProperty(key, value);
  }

  private static Checkbox bind(final StagedModSelection selection, final String id) throws Exception {
    final Checkbox checkbox = new Checkbox();
    final var binding = ModsScreen.class.getDeclaredMethod("bindSelection", Checkbox.class, StagedModSelection.class, String.class);
    binding.setAccessible(true);
    binding.invoke(null, checkbox, selection, id);
    return checkbox;
  }

  private static void confirm(final StagedModSelection selection, final MessageBoxResult result, final Runnable save, final Runnable close) throws Throwable {
    final var confirmation = CampaignSelectionScreen.class.getDeclaredMethod("confirmModSelection", StagedModSelection.class, MessageBoxResult.class, Runnable.class, Runnable.class);
    confirmation.setAccessible(true);
    try { confirmation.invoke(null, selection, result, save, close); }
    catch(final InvocationTargetException failure) { throw failure.getCause(); }
  }

  @Test void showingAndRetogglingLegacyDefaultsDoesNotRewriteRawSelection() throws Exception {
    final var requested = new HashSet<>(Set.of("lod", "custom_gameplay"));
    final var selection = new StagedModSelection(requested, ManagedModProfile.MOD_IDS);
    final var checkbox = bind(selection, "uihd");
    assertTrue(checkbox.isChecked());
    assertEquals(Set.of("lod", "custom_gameplay"), requested);
    checkbox.setChecked(false);
    assertEquals(Map.of("uihd", false), selection.choices());
    assertFalse(ManagedModProfile.effective(requested, ManagedModProfile.MOD_IDS, selection.choices()).contains("uihd"));
    assertFalse(Files.exists(this.preferences));
    checkbox.setChecked(true);
    assertFalse(selection.hasChanges());
    assertEquals(Set.of("lod", "custom_gameplay"), requested);
    checkbox.setChecked(true); // Checkbox fires even for an unchanged value.
    assertFalse(selection.hasChanges());
  }

  @Test void noCancelsBothCustomCampaignAndVisualChoicesWithoutTouchingExistingPreferences() throws Throwable {
    ManagedModProfile.recordSelection("fmvhd", false);
    final byte[] savedPreferences = Files.readAllBytes(this.preferences);
    final var persistedCampaign = new HashSet<>(Set.of("lod", "custom_gameplay"));
    final var stagedCampaign = new HashSet<>(persistedCampaign);
    final var selection = new StagedModSelection(stagedCampaign, ManagedModProfile.MOD_IDS);
    bind(selection, "uihd").setChecked(false);
    bind(selection, "custom_gameplay").setChecked(false);
    final int[] closed = {0};
    confirm(selection, MessageBoxResult.NO, () -> fail("NO cannot save the campaign"), () -> closed[0]++);
    assertEquals(1, closed[0]);
    assertEquals(Set.of("lod", "custom_gameplay"), persistedCampaign);
    assertArrayEquals(savedPreferences, Files.readAllBytes(this.preferences));
    assertTrue(ManagedModProfile.effective(persistedCampaign, ManagedModProfile.MOD_IDS).contains("uihd"));
  }

  @Test void yesAtomicallyPersistsAllVisualChoicesAndThenUpdatesCampaign() throws Throwable {
    final var requested = new HashSet<>(Set.of("lod", "custom_gameplay"));
    final var selection = new StagedModSelection(requested, ManagedModProfile.MOD_IDS);
    bind(selection, "uihd").setChecked(false);
    bind(selection, "fmvhd").setChecked(false);
    bind(selection, "custom_gameplay").setChecked(false);
    final var campaign = new HashSet<>(Set.of("lod", "custom_gameplay"));
    final int[] closed = {0};
    confirm(selection, MessageBoxResult.YES, () -> {
      final var effective = ManagedModProfile.effective(requested, ManagedModProfile.MOD_IDS);
      assertFalse(effective.contains("uihd"));
      assertFalse(effective.contains("fmvhd"));
      campaign.clear();
      campaign.addAll(requested);
    }, () -> closed[0]++);
    assertEquals(Set.of("lod"), campaign);
    assertEquals(1, closed[0]);
    assertFalse(Files.readString(this.preferences).contains("custom_gameplay"));
    final var reopened = new StagedModSelection(campaign, ManagedModProfile.MOD_IDS);
    assertFalse(bind(reopened, "uihd").isChecked());
    bind(reopened, "uihd").setChecked(true);
    reopened.accept();
    assertTrue(ManagedModProfile.effective(Set.of("lod"), ManagedModProfile.MOD_IDS).contains("uihd"));
  }

  @Test void failedPreferenceBatchKeepsAllBytesAndCampaignAndMenuOpenForRetryOrCancel() throws Throwable {
    Files.writeString(this.preferences, "future=true\nuihd=invalid\n");
    final byte[] before = Files.readAllBytes(this.preferences);
    final var selection = new StagedModSelection(new HashSet<>(Set.of("lod", "uihd", "fmvhd")), ManagedModProfile.MOD_IDS);
    bind(selection, "uihd").setChecked(false);
    bind(selection, "fmvhd").setChecked(false);
    assertThrows(IOException.class, () -> confirm(selection, MessageBoxResult.YES,
      () -> fail("Failed preference batch cannot save campaign"), () -> fail("Failed preference batch cannot close menu")));
    assertArrayEquals(before, Files.readAllBytes(this.preferences));
    assertEquals(Map.of("uihd", false, "fmvhd", false), selection.choices());
    assertFalse(bind(selection, "uihd").isChecked());
    Files.writeString(this.preferences, "future=true\n");
    selection.accept();
    assertEquals(Set.of("lod", "envhd", "charhd", "fxhd", "scbackgroundhd", "modelshd"), ManagedModProfile.effective(Set.of("lod"), ManagedModProfile.MOD_IDS));
  }

  @Test void newCampaignPreviewReopenAndCancellationDoNotPersistUntilAccepted() throws Exception {
    final var requested = new HashSet<>(ManagedModProfile.MOD_IDS);
    final var selection = new StagedModSelection(requested, ManagedModProfile.MOD_IDS);
    bind(selection, "uihd").setChecked(false);
    assertFalse(bind(selection, "uihd").isChecked());
    assertFalse(ManagedModProfile.effective(requested, ManagedModProfile.MOD_IDS, selection.choices()).contains("uihd"));
    assertFalse(Files.exists(this.preferences));
    // An abandoned new campaign drops this local selection; a fresh one still has the default.
    assertTrue(new StagedModSelection(new HashSet<>(ManagedModProfile.MOD_IDS), ManagedModProfile.MOD_IDS).isEnabled("uihd"));
    selection.accept();
    assertFalse(ManagedModProfile.effective(Set.of("lod"), ManagedModProfile.MOD_IDS).contains("uihd"));
  }

  @Test void unmanagedCheckboxesKeepUpstreamSelectionAndNeverWritePreferences() throws Exception {
    System.clearProperty(ManagedModProfile.PREFERENCES_PROPERTY);
    final var requested = new HashSet<>(Set.of("lod", "custom"));
    final var selection = new StagedModSelection(requested, ManagedModProfile.MOD_IDS);
    assertFalse(bind(selection, "uihd").isChecked());
    bind(selection, "uihd").setChecked(true);
    bind(selection, "custom").setChecked(false);
    selection.accept();
    assertEquals(Set.of("lod", "uihd"), requested);
    assertEquals(requested, ManagedModProfile.effective(requested, ManagedModProfile.MOD_IDS, selection.choices()));
    assertFalse(Files.exists(this.preferences));
  }

  @Test void originalProfileCannotDisplayOrPersistAnIneffectiveOptionalSelection() throws Exception {
    System.setProperty(ManagedModProfile.PROFILE_PROPERTY, "original");
    final var requested = new HashSet<>(ManagedModProfile.MOD_IDS);
    final var selection = new StagedModSelection(requested, ManagedModProfile.MOD_IDS);
    for(final String id : Set.of("scbackgroundhd", "modelshd")) {
      final Checkbox checkbox = bind(selection, id);
      assertTrue(checkbox.isDisabled());
      assertFalse(checkbox.isChecked());
      checkbox.setChecked(true);
      assertFalse(checkbox.isChecked());
      assertFalse(selection.isEnabled(id));
    }
    assertFalse(selection.hasChanges());
    assertTrue(bind(selection, "uihd").isChecked());
    selection.accept();
    assertFalse(Files.exists(this.preferences));
  }

  @Test void failureAlertIsImmediatelyOnTheActiveStackWithoutRenderingHiddenCampaignScreen() throws Exception {
    final MenuStack stack = new MenuStack();
    final var screensField = MenuStack.class.getDeclaredField("screens");
    screensField.setAccessible(true);
    @SuppressWarnings("unchecked") final var screens = (java.util.Deque<MenuScreen>)screensField.get(stack);
    // Model the already-open campaign/mods stack without registering window input handlers.
    final MenuScreen campaign = new MenuScreen() { @Override protected void render() {} };
    final MenuScreen mods = new MenuScreen() { @Override protected void render() {} };
    screens.push(campaign);
    screens.push(mods);
    final var alert = ModsScreen.class.getDeclaredMethod("showSaveFailure", MenuStack.class);
    alert.setAccessible(true);
    alert.invoke(null, stack);
    assertEquals(3, screens.size());
    assertInstanceOf(MessageBoxScreen.class, screens.peek());
    assertSame(stack, screens.peek().getStack());
    assertSame(mods, screens.stream().skip(1).findFirst().orElseThrow());
  }
}
