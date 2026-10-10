package legend.definitive.manager;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import javax.accessibility.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.sdl.SDLGamepad.*;

/** Actual shipped components and deterministic async seams, entirely headless. */
class UiHardeningTest {
  @TempDir Path temporary;
  @BeforeEach void realPath() throws Exception { this.temporary = this.temporary.toRealPath(); }
  @Test void accessibleCheckboxTogglesPersistentChosenFilesAndNotifiesConfirmation() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var model = new DefaultListModel<PickerFiles.Entry>();
      final Path path = this.temporary.resolve("disc.bin"); model.addElement(new PickerFiles.Entry(path, false));
      final var changes = new AtomicInteger(); final var list = new PickerFiles.FileList(model, entry -> fail("File is not a folder"), changes::incrementAndGet);
      list.setSelectedIndex(0);
      final var checkbox = list.getAccessibleContext().getAccessibleChild(0).getAccessibleContext().getAccessibleChild(0).getAccessibleContext();
      assertTrue(checkbox.getAccessibleAction().doAccessibleAction(0));
      assertEquals(List.of(path), list.chosen()); assertEquals(1, changes.get());
      final var refreshed = list.getAccessibleContext().getAccessibleChild(0).getAccessibleContext().getAccessibleChild(0).getAccessibleContext();
      assertTrue(refreshed.getAccessibleStateSet().contains(AccessibleState.CHECKED));
      assertTrue(refreshed.getAccessibleAction().doAccessibleAction(0)); assertTrue(list.chosen().isEmpty()); assertEquals(2, changes.get());
    });
  }
  @Test void keyboardAndAccessibilityShareSelectionAcrossFolderChangesWithoutFilesystemReads() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var model = new DefaultListModel<PickerFiles.Entry>(); final var folders = new AtomicInteger();
      final var list = new PickerFiles.FileList(model, entry -> folders.incrementAndGet(), () -> { });
      final Path one = this.temporary.resolve("nonexistent-folder/disc-one.bin"), two = this.temporary.resolve("another/disc-two.bin");
      model.addElement(new PickerFiles.Entry(one, false)); list.setSelectedIndex(0); list.getActionMap().get("activate").actionPerformed(null);
      model.clear(); model.addElement(new PickerFiles.Entry(two, false)); list.setSelectedIndex(0);
      list.getAccessibleContext().getAccessibleChild(0).getAccessibleContext().getAccessibleChild(0).getAccessibleContext().getAccessibleAction().doAccessibleAction(0);
      assertEquals(List.of(one, two), list.chosen());
      model.addElement(new PickerFiles.Entry(this.temporary.resolve("nonexistent-folder"), true)); list.setSelectedIndex(1); list.activate(); assertEquals(1, folders.get());
      assertEquals(List.of(one, two), list.chosen());
    });
  }
  @Test void blockedFolderLoadKeepsEdtResponsiveAndCannotReplaceNewerResults() throws Exception {
    final var firstStarted = new CountDownLatch(1); final var finishFirst = new CountDownLatch(1); final var secondDone = new CountDownLatch(1);
    final var delivered = new CopyOnWriteArrayList<Path>(); final var requests = new AtomicReference<PickerFiles.Requests>();
    final Path first = this.temporary.resolve("slow"), second = this.temporary.resolve("fast");
    SwingUtilities.invokeAndWait(() -> {
      requests.set(new PickerFiles.Requests((path, directories) -> {
        assertFalse(SwingUtilities.isEventDispatchThread());
        if(path.equals(first)) { firstStarted.countDown(); while(finishFirst.getCount() > 0) { try { finishFirst.await(20, TimeUnit.MILLISECONDS); } catch(final InterruptedException ignored) { } } }
        return List.of(new PickerFiles.Entry(path.resolve("disc.bin"), false));
      }));
      requests.get().open(first, false, entries -> delivered.add(first), error -> fail(error));
    });
    assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
    final var heartbeat = new CountDownLatch(1); SwingUtilities.invokeLater(heartbeat::countDown); assertTrue(heartbeat.await(1, TimeUnit.SECONDS));
    SwingUtilities.invokeAndWait(() -> requests.get().open(second, false, entries -> { delivered.add(second); secondDone.countDown(); }, error -> fail(error)));
    assertTrue(secondDone.await(5, TimeUnit.SECONDS)); finishFirst.countDown();
    SwingUtilities.invokeAndWait(() -> requests.get().close());
    assertEquals(List.of(second), delivered, "Cancelled/older folder must never replace a newer folder");
  }
  @Test void closingPickerCancelsOutstandingPublication() throws Exception {
    final var start = new CountDownLatch(1); final var finish = new CountDownLatch(1); final var finished = new CountDownLatch(1); final var published = new AtomicBoolean();
    final var requests = new AtomicReference<PickerFiles.Requests>();
    SwingUtilities.invokeAndWait(() -> {
      requests.set(new PickerFiles.Requests((path, directories) -> { start.countDown(); try { finish.await(); } finally { finished.countDown(); } return List.of(); }));
      requests.get().open(this.temporary, false, entries -> published.set(true), failure -> published.set(true));
    });
    assertTrue(start.await(5, TimeUnit.SECONDS)); SwingUtilities.invokeAndWait(() -> requests.get().close()); finish.countDown(); assertTrue(finished.await(5, TimeUnit.SECONDS));
    SwingUtilities.invokeAndWait(() -> { }); assertFalse(published.get());
  }
  @Test void disconnectOrReleaseOneControllerDoesNotReleaseAnotherHeldConfirm() {
    final var input = new NavigationInput(); final long now = 1_000_000_000L;
    assertTrue(input.press(NavigationInput.Source.BUTTON, 11, KeyEvent.VK_ENTER, now));
    assertFalse(input.press(NavigationInput.Source.BUTTON, 22, KeyEvent.VK_ENTER, now + 20_000_000));
    input.release(NavigationInput.Source.BUTTON, 22, KeyEvent.VK_ENTER); input.releaseDevice(22);
    assertFalse(input.press(NavigationInput.Source.BUTTON, 11, KeyEvent.VK_ENTER, now + 1_000_000_000L));
    input.releaseDevice(11); assertTrue(input.press(NavigationInput.Source.BUTTON, 11, KeyEvent.VK_ENTER, now + 2_000_000_000L));
  }
  @Test void controllerAxesHaveSeparateIdentityAndNeutralOneDoesNotResetAnother() {
    final var first = new DeckControls.PadState(); final var second = new DeckControls.PadState();
    first.axis(SDL_GAMEPAD_AXIS_LEFTY, 24000); second.axis(SDL_GAMEPAD_AXIS_LEFTX, -24000);
    assertEquals(KeyEvent.VK_DOWN, first.directionKey()); assertEquals(KeyEvent.VK_LEFT, second.directionKey());
    second.axis(SDL_GAMEPAD_AXIS_LEFTX, 0); assertEquals(0, second.directionKey()); assertEquals(KeyEvent.VK_DOWN, first.directionKey());
  }
  @Test void intentionalStopHasIdenticalProcessClassification() {
    for(final int status : new int[]{0, 130, 143}) assertTrue(ProcessResult.successful(status));
    for(final int status : new int[]{1, 2, 127, 137}) assertFalse(ProcessResult.successful(status));
  }
  @Test void launcherHasManualRetryAndUnavailableRestoreIsDisabled() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        final var view = new ManagerView(null, this.temporary, this.temporary.resolve("not-installed"));
        final var screen = ManagerView.class.getDeclaredField("screen"); screen.setAccessible(true); screen.set(view, ManagerView.Screen.LAUNCHER);
        final var render = ManagerView.class.getDeclaredMethod("render"); render.setAccessible(true); render.invoke(view);
        final var all = new java.util.ArrayList<Component>(); InstallStoreTest.collect(view, all);
        assertTrue(all.stream().anyMatch(component -> component instanceof JButton button && button.getText().equals("Check for updates") && button.isEnabled()));
        final var restore = all.stream().filter(component -> component instanceof JButton button && button.getText().equals("Restore version")).map(component -> (JButton)component).findFirst().orElseThrow();
        assertFalse(restore.isEnabled());
      } catch(final ReflectiveOperationException error) { throw new RuntimeException(error); }
    });
  }
  @Test void storageReviewAllowsOnlyRemovableItemsAndRequiresSelectedOnlyConfirmation() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var inventory = new InstallStore.RetentionInventory(List.of(new InstallStore.RetainedItem("releases/inactive", 4096, true, "Inactive verified engine"), new InstallStore.RetainedItem("data/private", 8192, false, "Private saves/settings")), true, "Complete");
      final var confirmed = new AtomicReference<List<String>>(); final var removed = new AtomicReference<List<String>>(); final var accept = new AtomicBoolean();
      final var panel = new StorageReviewPanel(inventory, selected -> { confirmed.set(selected); return accept.get(); }, removed::set);
      assertFalse(panel.removeAction().isEnabled()); panel.entries().setSelectedIndex(1); panel.entries().getActionMap().get("activate").actionPerformed(null);
      assertTrue(panel.selectedPaths().isEmpty());
      final var protectedBox = panel.entries().getAccessibleContext().getAccessibleChild(1).getAccessibleContext().getAccessibleChild(0).getAccessibleContext();
      assertFalse(protectedBox.getAccessibleStateSet().contains(AccessibleState.ENABLED)); protectedBox.getAccessibleAction().doAccessibleAction(0); assertTrue(panel.selectedPaths().isEmpty());
      panel.entries().setSelectedIndex(0); panel.entries().getActionMap().get("activate").actionPerformed(null); assertEquals(List.of("releases/inactive"), panel.selectedPaths()); assertTrue(panel.removeAction().isEnabled());
      panel.removeAction().doClick(); assertEquals(panel.selectedPaths(), confirmed.get()); assertNull(removed.get(), "Declined confirmation must not prune");
      accept.set(true); panel.removeAction().doClick(); assertEquals(List.of("releases/inactive"), removed.get());
    });
  }
  @Test void incompleteStorageDiscoveryDisablesSelectionAndRemovalDespiteRemovableFlags() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var panel = new StorageReviewPanel(new InstallStore.RetentionInventory(List.of(new InstallStore.RetainedItem("releases/unknown", 1024, true, "Ambiguous ownership")), false, "Ownership discovery incomplete"), selected -> fail("Cannot confirm incomplete inventory"), selected -> fail("Cannot prune incomplete inventory"));
      panel.entries().setSelectedIndex(0); panel.entries().getActionMap().get("activate").actionPerformed(null);
      final var box = panel.entries().getAccessibleContext().getAccessibleChild(0).getAccessibleContext().getAccessibleChild(0).getAccessibleContext(); box.getAccessibleAction().doAccessibleAction(0);
      assertTrue(panel.selectedPaths().isEmpty()); assertFalse(panel.removeAction().isEnabled()); panel.removeAction().doClick();
    });
  }
  @Test void storageSelectionUsesPruneApiAndRefusesStaleOwnershipWithoutRemovingAnything() throws Exception {
    final var fixture = new InstallStoreTest(); fixture.temporary = this.temporary;
    final var store = new InstallStore(this.temporary.resolve("storage-install"));
    for(int version = 1; version <= 4; version++) store.install(fixture.pack("storage-v" + version, PackageManifest.hostPlatform()));
    final var inventory = store.retentionInventory(); assertTrue(inventory.complete());
    final var removable = inventory.items().stream().filter(InstallStore.RetainedItem::removable).findFirst().orElseThrow();
    final var selection = new AtomicReference<List<String>>();
    SwingUtilities.invokeAndWait(() -> {
      final var panel = new StorageReviewPanel(inventory, selected -> true, selection::set);
      final int index = inventory.items().indexOf(removable); panel.entries().setSelectedIndex(index); panel.entries().getActionMap().get("activate").actionPerformed(null); panel.removeAction().doClick();
    });
    assertEquals(List.of(removable.path()), selection.get());
    final Path unknown = Files.createDirectory(store.root().resolve("releases/unknown-owner"));
    assertThrows(java.io.IOException.class, () -> store.pruneRetained(selection.get(), InstallProgress.NONE)); assertTrue(Files.exists(store.root().resolve(removable.path())));
    Files.delete(unknown); final var protectedData = inventory.items().stream().filter(item -> item.path().startsWith("data/")).findFirst().orElseThrow();
    assertThrows(java.io.IOException.class, () -> store.pruneRetained(List.of(removable.path(), protectedData.path()), InstallProgress.NONE)); assertTrue(Files.exists(store.root().resolve(removable.path())));
    store.pruneRetained(selection.get(), InstallProgress.NONE); assertFalse(Files.exists(store.root().resolve(removable.path()))); assertTrue(Files.exists(store.root().resolve(protectedData.path()))); store.verifyInstalled();
  }

  @Test void recoveredStateIsProminentAndAcknowledgmentClearsOnlyPendingFlag() throws Exception {
    final var fixture = new InstallStoreTest(); fixture.temporary = this.temporary;
    final var store = new InstallStore(this.temporary.resolve("recovery-ui")); store.install(fixture.pack("recovery-package", PackageManifest.hostPlatform()));
    final var state = store.state(); state.setProperty("recoveryPending", "true"); state.setProperty("recoveryNotice", "Earlier verified files recovered; newer private data stays separate."); state.setProperty("recoveryRestoredRelease", state.getProperty("version")); state.setProperty("recoveryRestoredData", store.data(state).toString()); state.setProperty("recoveryPreservedLocations", store.root().resolve("data/preserved-generation").toString()); InstallStore.atomicProperties(store.root().resolve("state.properties"), state);
    final var view = new AtomicReference<ManagerView>();
    SwingUtilities.invokeAndWait(() -> {
      try {
        view.set(new ManagerView(null, null, store.root()));
      } catch(final RuntimeException failure) { throw failure; }
    });
    final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    final var acknowledgment = new AtomicReference<JButton>();
    while(acknowledgment.get() == null && System.nanoTime() < deadline) {
      SwingUtilities.invokeAndWait(() -> { final var all = new java.util.ArrayList<Component>(); InstallStoreTest.collect(view.get(), all); acknowledgment.set(all.stream().filter(component -> component instanceof JButton button && button.getText().equals("Use verified recovered state")).map(component -> (JButton)component).findFirst().orElse(null)); });
      if(acknowledgment.get() == null) Thread.sleep(10);
    }
    assertNotNull(acknowledgment.get());
    SwingUtilities.invokeAndWait(() -> { final var all = new java.util.ArrayList<Component>(); InstallStoreTest.collect(view.get(), all); assertFalse(all.stream().anyMatch(component -> component instanceof JButton button && button.getText().equals("Play"))); acknowledgment.get().doClick(); });
    while(Boolean.parseBoolean(store.state().getProperty("recoveryPending", "false")) && System.nanoTime() < deadline) Thread.sleep(10);
    // Persisting the flag precedes worker cleanup and the next identity inspection.
    awaitIdle(view.get());
    assertFalse(Boolean.parseBoolean(store.state().getProperty("recoveryPending", "false"))); assertEquals(state.getProperty("recoveryPreservedLocations"), store.state().getProperty("recoveryPreservedLocations"));
    final String report = DiagnosticsReport.collect(store.root()); assertTrue(report.contains("Earlier verified files recovered")); assertTrue(report.contains(state.getProperty("recoveryPreservedLocations")));
  }

  @Test void recoveryAcknowledgmentIsReachableWithMissingDiscsOrExtractionInPortableMaintenance() throws Exception {
    for(final boolean portable : new boolean[]{false, true}) for(final boolean haveDiscs : new boolean[]{false, true}) {
      final var fixture = new InstallStoreTest(); fixture.temporary = this.temporary;
      final var store = new InstallStore(this.temporary.resolve("pending-" + portable + '-' + haveDiscs));
      final Path pack = fixture.pack("pending-package-" + portable + '-' + haveDiscs, PackageManifest.hostPlatform()); store.install(pack);
      if(haveDiscs) for(final String id : DiscImporter.IDS) Files.write(store.root().resolve("isos/" + id + ".bin"), InstallStoreTest.disc(id));
      final var state = store.state(); state.setProperty("recoveryPending", "true"); state.setProperty("recoveryNotice", "Earlier verified state recovered; newer private data retained."); state.setProperty("recoveryRestoredRelease", state.getProperty("version")); state.setProperty("recoveryRestoredData", store.data(state).toString()); InstallStore.atomicProperties(store.root().resolve("state.properties"), state);
      final var view = new AtomicReference<ManagerView>(); final var acknowledge = new AtomicReference<JButton>();
      SwingUtilities.invokeAndWait(() -> {
        view.set(new ManagerView(null, portable ? pack : null, store.root()));
        final var all = new java.util.ArrayList<Component>(); InstallStoreTest.collect(view.get(), all);
        assertTrue(all.stream().filter(component -> component instanceof JButton).map(component -> (JButton)component).noneMatch(JButton::isEnabled), "No operation may race the initial recovery metadata check");
      });
      final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while(acknowledge.get() == null && System.nanoTime() < deadline) {
        SwingUtilities.invokeAndWait(() -> { final var all = new java.util.ArrayList<Component>(); InstallStoreTest.collect(view.get(), all); acknowledge.set(all.stream().filter(component -> component instanceof JButton button && button.getText().equals("Use verified recovered state") && button.isEnabled()).map(component -> (JButton)component).findFirst().orElse(null)); });
        if(acknowledge.get() == null) Thread.sleep(10);
      }
      assertNotNull(acknowledge.get(), "Pending recovery must be reachable before preparation/maintenance"); SwingUtilities.invokeAndWait(() -> acknowledge.get().doClick());
      final var selection = new AtomicReference<JButton>();
      while(selection.get() == null && System.nanoTime() < deadline) {
        SwingUtilities.invokeAndWait(() -> { final var all = new java.util.ArrayList<Component>(); InstallStoreTest.collect(view.get(), all); selection.set(all.stream().filter(component -> component instanceof JButton button && button.isEnabled() && button.getText().equals(haveDiscs ? "Use installed discs" : "Choose disc files")).map(component -> (JButton)component).findFirst().orElse(null)); });
        if(selection.get() == null) Thread.sleep(10);
      }
      assertNotNull(selection.get(), "After explicit acknowledgment, preparation must be available even without its marker"); assertFalse(Boolean.parseBoolean(store.state().getProperty("recoveryPending")));
    }
  }

  @Test void recoveredUninstalledStateOffersAcknowledgmentBeforePortableReinstall() throws Exception {
    final var fixture = new InstallStoreTest(); fixture.temporary = this.temporary;
    final Path pack = fixture.pack("uninstalled-recovery-package", PackageManifest.hostPlatform());
    final var store = new InstallStore(this.temporary.resolve("uninstalled-recovery")); store.install(pack);
    final Path data = store.data(store.state()); Files.writeString(data.resolve("saves/owner"), "preserved-save");
    store.uninstall(false, InstallProgress.NONE); Files.writeString(store.root().resolve("state.properties"), "damaged state");
    final var recovered = new InstallStore(store.root()); assertEquals("true", recovered.state().getProperty("recoveryPending"));
    assertFalse(Files.exists(store.root().resolve("releases").resolve(recovered.state().getProperty("version"))));
    final var view = new AtomicReference<ManagerView>(); SwingUtilities.invokeAndWait(() -> view.set(new ManagerView(null, pack, store.root())));
    final JButton acknowledge = awaitButton(view.get(), "Use verified recovered state"); SwingUtilities.invokeAndWait(acknowledge::doClick);
    final JButton reinstall = awaitButton(view.get(), "Reinstall"); assertEquals("false", recovered.state().getProperty("recoveryPending"));
    SwingUtilities.invokeAndWait(reinstall::doClick); assertNotNull(awaitButton(view.get(), "Choose disc files"));
    assertEquals("preserved-save", Files.readString(data.resolve("saves/owner"))); assertFalse(Boolean.parseBoolean(recovered.state().getProperty("uninstalled", "false")));
    assertTrue(DiagnosticsReport.collect(store.root()).contains("Installation state was damaged"));
  }
  private static void awaitIdle(final ManagerView view) throws Exception {
    final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while(System.nanoTime() < deadline) {
      final var busy = new AtomicBoolean();
      SwingUtilities.invokeAndWait(() -> busy.set(view.isBusy()));
      if(!busy.get()) return;
      Thread.sleep(20);
    }
    fail("Recovery worker did not finish within the test budget");
  }

  private static JButton awaitButton(final ManagerView view, final String label) throws Exception {
    final var found = new AtomicReference<JButton>(); final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while(found.get() == null && System.nanoTime() < deadline) {
      SwingUtilities.invokeAndWait(() -> { final var all = new java.util.ArrayList<Component>(); InstallStoreTest.collect(view, all); found.set(all.stream().filter(component -> component instanceof JButton button && button.isEnabled() && button.getText().equals(label)).map(component -> (JButton)component).findFirst().orElse(null)); });
      if(found.get() == null) Thread.sleep(10);
    }
    assertNotNull(found.get(), "Expected enabled action: " + label); return found.get();
  }

}
