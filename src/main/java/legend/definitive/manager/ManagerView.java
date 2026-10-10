// Definitive guided setup and launcher (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;

/** One action per setup step; a Play-first launcher after setup. Backend work stays off the UI thread. */
final class ManagerView extends JPanel {
  static final Color PAPER = new Color(0xf6f4ef), INK = new Color(0x202b28), MUTED = new Color(0x59685f), GREEN = new Color(0x244e40), LINE = new Color(0xdaddd4);
  private static final String UI_FONT = java.util.Arrays.stream(new String[]{"Helvetica Neue", "Noto Sans", "DejaVu Sans", Font.SANS_SERIF}).filter(name -> java.util.Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()).contains(name)).findFirst().orElse(Font.SANS_SERIF);
  private final JFrame frame;
  private final Path packageRoot;
  private final Runnable close;
  private Path root;
  private final JPanel body = column();
  private final JPanel content = column();
  private int horizontalInset = -1;
  private final JLabel status = label("", 15, MUTED);
  private final JLabel updates = label("", 14, MUTED);
  private final JProgressBar progress = new JProgressBar();
  private final JPanel progressPanel = card();
  private final JLabel progressPhase = label("Starting…", 18, INK);
  private final JLabel progressPercent = label("0%", 14, MUTED);
  private final JLabel progressElapsed = label("", 12, MUTED);
  private final JTextField destination;
  private boolean busy;
  private boolean failed;
  private long operation;
  enum Screen {
    INSTALL, DISCS, STEAM, MAINTENANCE, UNINSTALLED, LAUNCHER, UPDATE, UPDATED, STORAGE, RECOVERY;
    boolean launcher() { return this == LAUNCHER || this == UPDATE || this == UPDATED || this == STORAGE || this == RECOVERY; }
    int step() { return this == DISCS ? 1 : this == STEAM ? 2 : 0; }
  }
  private Screen screen = Screen.INSTALL;
  private InstallStore.RetentionInventory storageInventory;
  private long identityRequest;
  private boolean inspectingIdentity;
  private boolean identityReady;
  private boolean recoveryPending;
  private String recoveryNotice = "";
  private String recoveryRestoredRelease = "";
  private String recoveryRestoredData = "";
  private String recoveryPreservedLocations = "";
  private boolean restoreAvailable;
  private String installedIdentity = "Installed release";
  private String fullIdentity = "";
  private boolean checkingUpdates;
  private java.time.Instant lastChecked;
  private String updateStatus = "Updates have not been checked";
  private DiscImporter.Existing installedDiscs;
  private ReleaseUpdates.Candidate candidate;
  private InstallProgress currentProgress = InstallProgress.NONE;
  private long started;
  private String progressDetail = "";
  private final Timer elapsed = new Timer(1000, e -> this.progressStatus());

  ManagerView(final JFrame frame, final Path packageRoot, final Path root) {
    this(frame, packageRoot, root, frame == null ? () -> { } : frame::dispose);
  }
  ManagerView(final JFrame frame, final Path packageRoot, final Path root, final Runnable close) {
    this.close = close;
    this.frame = frame; this.packageRoot = packageRoot; this.root = root;
    if(Files.isRegularFile(root.resolve("state.properties"))) {
      this.inspectingIdentity = true;
      this.screen = packageRoot == null ? Screen.LAUNCHER : Files.isRegularFile(root.resolve(".definitive-owned")) ? Screen.MAINTENANCE : Screen.INSTALL;
      if(this.screen.launcher()) {
        try { DiscImporter.validateSet(root.resolve("isos")); if(!new InstallStore(root).discsPrepared()) this.screen = Screen.DISCS; }
        catch(final Exception e) { this.screen = Screen.DISCS; }
      }
    }
    this.destination = new JTextField(root.toString()); this.destination.setFont(font(15, false)); this.destination.setAlignmentX(LEFT_ALIGNMENT);
    this.destination.setBackground(Color.WHITE); this.destination.setForeground(INK); this.destination.setBorder(BorderFactory.createCompoundBorder(roundedBorder(), BorderFactory.createEmptyBorder(12, 14, 12, 14)));
    this.destination.addFocusListener(new FocusAdapter() {
      @Override public void focusGained(final FocusEvent event) { ManagerView.this.destination.repaint(); }
      @Override public void focusLost(final FocusEvent event) { ManagerView.this.destination.repaint(); }
    });
    this.setLayout(new BorderLayout()); this.setBackground(PAPER);
    this.add(new Hero(), BorderLayout.WEST);
    final JPanel content = this.content; content.setBorder(BorderFactory.createEmptyBorder(36, 44, 26, 44));
    final JLabel eyebrow = label("DEFINITIVE", 12, GREEN); eyebrow.setFont(font(13, true).deriveFont(java.util.Map.of(java.awt.font.TextAttribute.TRACKING, 0.16f)));
    content.add(eyebrow); content.add(Box.createVerticalStrut(26)); content.add(this.body);
    this.progress.setIndeterminate(false); this.progress.setStringPainted(false); this.progress.setAlignmentX(LEFT_ALIGNMENT); this.progress.setMaximumSize(new Dimension(520, 8)); this.progress.setPreferredSize(new Dimension(520, 8)); this.progress.setBorderPainted(false);
    this.progress.setUI(new javax.swing.plaf.basic.BasicProgressBarUI() {
      @Override public void paint(final Graphics graphics, final JComponent component) {
        final Graphics2D g = (Graphics2D)graphics.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xe3e8e0)); g.fillRoundRect(0, 0, component.getWidth(), component.getHeight(), 8, 8);
        g.setColor(GREEN); g.fillRoundRect(0, 0, (int)(component.getWidth() * ManagerView.this.progress.getPercentComplete()), component.getHeight(), 8, 8); g.dispose();
      }
    });
    final JPanel phase = new JPanel(new BorderLayout(12, 0)); phase.setOpaque(false); phase.setAlignmentX(LEFT_ALIGNMENT); phase.setMaximumSize(new Dimension(520, 28));
    this.progressPhase.setFont(font(18, true)); phase.add(this.progressPhase, BorderLayout.CENTER); phase.add(this.progressPercent, BorderLayout.EAST);
    this.progressPanel.add(phase); this.progressPanel.add(Box.createVerticalStrut(18)); this.progressPanel.add(this.progress); this.progressPanel.add(Box.createVerticalStrut(14));
    this.status.setMaximumSize(new Dimension(480, 90)); this.progressPanel.add(this.status);
    this.progressPanel.add(Box.createVerticalStrut(10)); this.progressPanel.add(this.progressElapsed); this.progressPanel.setVisible(false);
    content.add(this.progressPanel); content.add(Box.createVerticalStrut(12)); content.add(Box.createVerticalGlue());
    this.updates.setMaximumSize(new Dimension(520, 50)); content.add(this.updates);
    content.add(Box.createVerticalStrut(10)); content.add(label("D-pad  Move     A  Select     B  Back", 12, MUTED));
    this.add(content, BorderLayout.CENTER);
    if(frame != null) {
      frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "back");
      frame.getRootPane().getActionMap().put("back", new AbstractAction() { @Override public void actionPerformed(final ActionEvent e) { ManagerView.this.goBack(); } });
    }
    this.render();
    if(frame != null) {
      frame.addWindowListener(new WindowAdapter() { @Override public void windowClosing(final WindowEvent e) { if(ManagerView.this.busy) ManagerView.this.message("Please wait for this operation to finish, or close the game first."); else frame.dispose(); } });
      if(this.screen.launcher()) this.checkUpdates(false);
    }
    if(Files.isRegularFile(this.root.resolve("state.properties"))) this.loadInstallationInfo();

  }

  @Override protected boolean isPaintingOrigin() { return true; }
  @Override public void paint(final Graphics graphics) {
    final Graphics2D smooth = smooth(graphics);
    try { super.paint(smooth); } finally { smooth.dispose(); }
  }
  private static Graphics2D smooth(final Graphics graphics) {
    final Graphics2D smooth = (Graphics2D)graphics.create();
    smooth.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    smooth.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    return smooth;
  }
  static JPanel surface(final LayoutManager layout) {
    return new JPanel(layout) {
      @Override protected boolean isPaintingOrigin() { return true; }
      @Override public void paint(final Graphics graphics) {
        final Graphics2D smooth = smooth(graphics);
        try { super.paint(smooth); } finally { smooth.dispose(); }
      }
    };
  }

  @Override public void doLayout() {
    final int inset = Math.max(36, (this.getWidth() - Math.max(340, Math.min(440, (int)(this.getWidth() * .36))) - 520) / 2);
    if(inset != this.horizontalInset) { this.horizontalInset = inset; this.content.setBorder(BorderFactory.createEmptyBorder(36, inset, 26, inset)); }
    super.doLayout();
  }

  private void render() {
    this.failed = false;
    this.body.removeAll();
    switch(this.screen) {
      case UPDATED -> this.updateFinished();
      case UPDATE -> { if(this.candidate == null) { this.screen = Screen.LAUNCHER; this.launcher(); } else this.updateScreen(); }
      case LAUNCHER -> this.launcher();
      case STORAGE -> this.storageScreen();
      case RECOVERY -> this.recoveryScreen();
      default -> this.setup();
    }
    if(this.inspectingIdentity) disableActions(this.body);
    this.body.revalidate(); this.body.repaint();
    this.updateStatus(); this.focusPrimaryAction();
  }
  private static void disableActions(final Container root) { for(final Component child : root.getComponents()) { if(child instanceof AbstractButton button) button.setEnabled(false); if(child instanceof Container children) disableActions(children); } }
  private void inspectExistingDiscs() { this.run("Checking installed discs", () -> DiscImporter.existing(new InstallStore(this.root), this.currentProgress), discs -> { this.installedDiscs = discs; this.render(); }); }
  private void focusPrimaryAction() {
    SwingUtilities.invokeLater(() -> {
      if(this.frame != null) {
        final JButton action = this.frame.getRootPane().getDefaultButton();
        if(action != null && action.isShowing() && action.isEnabled()) action.requestFocusInWindow();
      }
    });
  }
  private void heading(final String title, final String description) {
    final JLabel heading = copy(title, 34, INK); heading.setFont(font(34, true)); this.body.add(heading); this.body.add(Box.createVerticalStrut(12));
    this.body.add(copy(description, 17, MUTED)); this.body.add(Box.createVerticalStrut(26));
  }
  private void setup() {
    this.updates.setText("Community alpha · Built on Severed Chains");
    if(this.screen == Screen.UNINSTALLED) {
      this.heading("Definitive is uninstalled", "Your saves, settings and custom mods are retained.");
      this.body.add(infoCard("RETAINED FILES", "Ready whenever you return", this.root.resolve("data").toString()));
      this.body.add(Box.createVerticalStrut(24)); this.primary("Done", this.close);
      return;
    }
    if(this.screen == Screen.MAINTENANCE) {
      this.heading("Your installation", "Reinstall Definitive, or remove it from this device.");
      this.body.add(infoCard("LOCATION", "Legend of Dragoon: Definitive", this.root.toString())); this.body.add(Box.createVerticalStrut(24));
      this.primary("Reinstall", () -> { this.screen = Screen.INSTALL; this.install(); });
      this.body.add(Box.createVerticalStrut(12)); final JButton uninstall = button("Uninstall", false); uninstall.addActionListener(e -> this.uninstall()); this.setupActions(uninstall);
      return;
    }
    this.stages();
    switch(this.screen.step()) {
      case 0 -> {
        this.heading("Install Definitive", "Set up the game, HD artwork and launcher.");
        this.body.add(label("Install location", 13, MUTED)); this.body.add(Box.createVerticalStrut(8));
        this.destination.setMaximumSize(new Dimension(520, 48)); this.destination.setCaretPosition(0); this.body.add(this.destination); this.body.add(Box.createVerticalStrut(8));
        final JButton browse = button("Choose folder", false); browse.addActionListener(e -> this.chooseFolder()); this.body.add(browse); this.body.add(Box.createVerticalStrut(20));
        this.body.add(copy("Next, you’ll select your disc images. Your originals stay where they are.", 15, MUTED)); this.body.add(Box.createVerticalStrut(24));
        this.primary("Install Definitive", this::install);
      }
      case 1 -> {
        final boolean found = this.installedDiscs != null && this.installedDiscs.usable();
        this.heading(found ? "Your discs are here" : "Select your discs", found ? this.installedDiscs.detail() : "Choose the four US disc images, or archives containing them.");
        this.body.add(infoCard(found ? "EXISTING IMAGES" : "SUPPORTED FILES", found ? "No need to copy them again" : "BIN / raw ISO · ZIP · RAR · 7z", found ? "Reuse your checked discs, or choose files to compare and replace them." : "Your files are checked and copied before the game is prepared."));
        this.body.add(Box.createVerticalStrut(28));
        if(found) {
          this.primary("Use installed discs", this::useInstalledDiscs);
          this.body.add(Box.createVerticalStrut(12));
          final JButton select = button("Choose disc files", false); select.addActionListener(e -> this.chooseDiscs()); this.setupActions(select);
        } else this.primary("Choose disc files", this::chooseDiscs);
      }
      default -> {
        this.heading("You’re ready to play", "Add Definitive to Steam for Gaming Mode, or finish setup.");
        final String location = this.root.toString();
        final JPanel installed = infoCard("INSTALLATION VERIFIED", "Game and HD artwork ready", locationLabel(location));
        installed.setToolTipText(location); installed.getAccessibleContext().setAccessibleDescription(location); this.body.add(installed);
        this.body.add(Box.createVerticalStrut(12)); this.body.add(copy("Steam restarts to refresh your library. Your shortcuts are backed up.", 15, MUTED));
        this.body.add(Box.createVerticalStrut(28));
        this.primary("Add to Steam", () -> this.addSteam(true));
        this.body.add(Box.createVerticalStrut(12));
        final JButton skip = button("Finish without Steam", false); skip.addActionListener(e -> this.finish()); this.setupActions(skip);
      }
    }
  }
  private void install() {
    try { this.root = installationPath(this.destination.getText()); this.destination.setText(this.root.toString()); }
    catch(final Exception error) { this.showFailure(error); return; }
    this.run("Installing Definitive", () -> {
      final var store = new InstallStore(this.root); final InstallProgress installing = update -> this.currentProgress.report(new InstallProgress.Update(update.phase(), update.detail(), update.startPercent() * 75 / 100, update.endPercent() * 75 / 100, update.completed(), update.total()));
      final String result = PortableSetup.install(this.packageRoot, store, installing);
      store.verifyInstalled(); InstallLocation.record(store); final InstallProgress checking = update -> this.currentProgress.report(new InstallProgress.Update(update.phase(), update.detail(), 75 + update.startPercent() / 4, 75 + update.endPercent() / 4, update.completed(), update.total()));
      this.installedDiscs = DiscImporter.existing(store, checking); this.currentProgress.phase("Installation ready", "Choose whether to reuse or replace your disc images", 100); return result;
    }, () -> { this.screen = this.recoveryPending ? Screen.RECOVERY : Screen.DISCS; this.loadInstallationInfo(); this.render(); });
  }
  private void uninstall() {
    final JCheckBox delete = new JCheckBox("Delete ISOs", false); delete.setFont(font(18, false)); delete.setOpaque(false); delete.setPreferredSize(new Dimension(520, 52));
    final JPanel options = column(); options.add(copy("Remove Definitive and its Steam shortcut. Your saves, settings and custom mods stay here.", 18, INK)); options.add(Box.createVerticalStrut(18)); options.add(delete);
    options.add(copy("Steam will reopen after the library is refreshed.", 15, MUTED));
    if(!ManagerDialogs.confirm(this.frame, "Uninstall Definitive", options, "Uninstall")) return;
    final boolean deleteIsos = delete.isSelected();
    this.run("Uninstalling Definitive", () -> {
      final var store = new InstallStore(this.root);
      return store.withOperation(() -> { for(final var account : SteamLibrary.accounts()) SteamIntegration.remove(account, this.root, this.currentProgress); return store.uninstall(deleteIsos, this.currentProgress); });
    }, () -> { this.screen = Screen.UNINSTALLED; this.render(); });
  }
  private void setupActions(final JButton secondary) {
    final JPanel actions = new JPanel(new GridLayout(1, 2, 12, 0)); actions.setOpaque(false); actions.setAlignmentX(LEFT_ALIGNMENT); actions.setMaximumSize(new Dimension(520, 48));
    final JButton back = button("Back", false); back.addActionListener(e -> this.goBack()); actions.add(secondary); actions.add(back); this.body.add(actions);
  }
  private void launcher() {
    this.heading("Ready to play", "The Legend of Dragoon · Definitive");
    final JPanel identity = infoCard("INSTALLED RELEASE", this.installedIdentity, "Faithful or Definitive for new campaigns."); identity.setToolTipText(this.fullIdentity); identity.getAccessibleContext().setAccessibleDescription(this.fullIdentity); this.body.add(identity);
    this.body.add(Box.createVerticalStrut(30));
    this.primary("Play", () -> this.run("Game running", () -> {
      final var store = new InstallStore(this.root); final int code = store.play();
      if(!ProcessResult.successful(code)) throw new java.io.IOException("The game exited with code " + code + ". Details: " + store.gameLog());
      return "Game closed.";
    }, () -> { })).setEnabled(this.identityReady && !this.recoveryPending);
    this.body.add(Box.createVerticalStrut(16));
    final JPanel secondary = new JPanel(new GridLayout(1, 2, 12, 0)); secondary.setOpaque(false); secondary.setAlignmentX(LEFT_ALIGNMENT); secondary.setMaximumSize(new Dimension(520, 48));
    final JButton mods = button("Mods & artwork", false); mods.addActionListener(e -> this.mods());
    final JButton restore = button("Restore version", false); restore.addActionListener(e -> {
      if(ManagerDialogs.restore(this.frame)) this.run("Restoring version", () -> new InstallStore(this.root).rollback(), () -> { this.candidate = null; this.loadInstallationInfo(); this.render(); });
    });
    restore.setEnabled(this.restoreAvailable); restore.setToolTipText(this.restoreAvailable ? "Restore the verified previous version and its data snapshot" : "No verified previous version is available");
    secondary.add(mods); secondary.add(restore); this.body.add(secondary);
    this.body.add(Box.createVerticalStrut(12));
    final JPanel maintenanceActions = new JPanel(new GridLayout(1, 2, 12, 0)); maintenanceActions.setOpaque(false); maintenanceActions.setAlignmentX(LEFT_ALIGNMENT); maintenanceActions.setMaximumSize(new Dimension(520, 48));
    final JButton steam = button("Add to Steam library", false); steam.addActionListener(e -> this.addSteam(false)); maintenanceActions.add(steam);
    final JButton check = button(this.checkingUpdates ? "Checking…" : "Check for updates", false); check.setEnabled(!this.checkingUpdates); check.addActionListener(e -> this.checkUpdates(true)); maintenanceActions.add(check); this.body.add(maintenanceActions);
    this.body.add(Box.createVerticalStrut(8)); final JPanel storageActions = new JPanel(new GridLayout(1, this.candidate == null ? 1 : 2, 12, 0)); storageActions.setOpaque(false); storageActions.setAlignmentX(LEFT_ALIGNMENT); storageActions.setMaximumSize(new Dimension(520, 48));
    if(this.candidate != null) { final JButton update = button("Review update", false); update.addActionListener(e -> { this.screen = Screen.UPDATE; this.render(); }); storageActions.add(update); }
    final JButton storage = button("Manage storage", false); storage.addActionListener(e -> this.loadStorage()); storageActions.add(storage); this.body.add(storageActions);
  }
  private void loadStorage() {
    this.run("Checking managed storage", () -> new InstallStore(this.root).retentionInventory(), inventory -> { this.storageInventory = inventory; this.screen = Screen.STORAGE; this.render(); });
  }
  private void storageScreen() {
    this.heading("Manage storage", "Review retained files before choosing what to remove.");
    if(this.storageInventory == null) { this.body.add(copy("Load the current inventory before removing files.", 16, MUTED)); }
    else {
      final StorageReviewPanel review = new StorageReviewPanel(this.storageInventory, selected -> {
        final JTextArea chosen = new JTextArea("Remove only these selected inactive files?\n\n" + String.join("\n", selected) + "\n\nSaves, settings, custom mods, snapshots and recovery folders remain protected."); chosen.setEditable(false); chosen.setLineWrap(true); chosen.setWrapStyleWord(true); chosen.setFont(font(15, false)); chosen.setCaretPosition(0);
        return ManagerDialogs.confirm(this.frame, "Remove selected files", new JScrollPane(chosen), "Remove selected");
      }, selected -> this.run("Removing selected files", () -> {
        final var store = new InstallStore(this.root); final String message = store.pruneRetained(selected, this.currentProgress); return new StorageResult(message, store.retentionInventory());
      }, result -> { this.storageInventory = result.inventory(); this.screen = Screen.STORAGE; this.render(); this.message(result.message()); }));
      this.body.add(review);
    }
    this.body.add(Box.createVerticalStrut(14)); final JPanel actions = new JPanel(new GridLayout(1, 2, 12, 0)); actions.setOpaque(false); actions.setAlignmentX(LEFT_ALIGNMENT); actions.setMaximumSize(new Dimension(520, 48));
    final JButton back = button(this.recoveryPending ? "Back to recovery" : "Back to launcher", false); back.addActionListener(event -> { this.screen = this.recoveryPending ? Screen.RECOVERY : Screen.LAUNCHER; this.render(); }); actions.add(back);
    final JButton refresh = button("Refresh inventory", false); refresh.addActionListener(event -> this.loadStorage()); actions.add(refresh); this.body.add(actions); if(this.frame != null) this.frame.getRootPane().setDefaultButton(back);
  }
  private void recoveryScreen() {
    this.heading("Recovered installation", "Review the recovered state before continuing to play.");
    final JPanel notice = infoCard("RECOVERY REQUIRES REVIEW", "Earlier verified files are active", this.recoveryNotice.isBlank() ? "The last verified installation state was recovered. Newer private data remains protected." : progressSummary(this.recoveryNotice));
    notice.setToolTipText(this.recoveryRestoredRelease + "\n" + this.recoveryRestoredData); notice.getAccessibleContext().setAccessibleDescription("Recovery notice: " + this.recoveryNotice + " · Restored package: " + this.recoveryRestoredRelease + " · Restored private data: " + this.recoveryRestoredData); this.body.add(notice);
    this.body.add(Box.createVerticalStrut(18));
    this.primary("Use verified recovered state", () -> this.run("Acknowledging recovered state", () -> {
      final var store = new InstallStore(this.root); store.acknowledgeRecovery();
      if(Boolean.parseBoolean(store.state().getProperty("uninstalled", "false"))) return this.packageRoot == null ? Screen.UNINSTALLED : Screen.MAINTENANCE;
      try { DiscImporter.validateSet(this.root.resolve("isos")); return store.discsPrepared() ? Screen.LAUNCHER : Screen.DISCS; } catch(final java.io.IOException missingDiscs) { return Screen.DISCS; }
    }, next -> { this.recoveryPending = false; this.screen = next; this.loadInstallationInfo(); this.render(); }));
    this.body.add(Box.createVerticalStrut(12)); final JButton preserved = button("Review preserved data", false); preserved.addActionListener(event -> this.loadStorage()); this.body.add(preserved);
    this.body.add(Box.createVerticalStrut(12)); final JButton details = button("Recovery details", false); details.addActionListener(event -> {
      final JTextArea text = new JTextArea("Recovery notice:\n" + this.recoveryNotice + "\n\nVerified recovered package:\n" + this.recoveryRestoredRelease + "\n\nVerified recovered data:\n" + this.recoveryRestoredData + "\n\nPreserved locations:\n" + this.recoveryPreservedLocations); text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true); text.setFont(font(14, false)); text.setCaretPosition(0); ManagerDialogs.confirm(this.frame, "Recovery details", new JScrollPane(text), "Close");
    }); this.body.add(details);
  }
  private record StorageResult(String message, InstallStore.RetentionInventory inventory) { }

  private void stages() {
    final JPanel stages = new JPanel(new GridLayout(1, 3, 12, 0)); stages.setOpaque(false); stages.setAlignmentX(LEFT_ALIGNMENT);
    final String[] names = {"01   Install", "02   Your discs", "03   Steam"};
    for(int i = 0; i < 3; i++) { final JLabel item = label(names[i], 14, i == this.screen.step() ? GREEN : MUTED); item.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, i == this.screen.step() ? GREEN : new Color(0xdedfd7))); stages.add(item); }
    stages.setMaximumSize(new Dimension(520, 32)); this.body.add(stages); this.body.add(Box.createVerticalStrut(32));
  }
  private void updateScreen() {
    this.heading("Update Definitive", "Your saves and settings stay in place.");
    final JPanel release = infoCard("AVAILABLE RELEASE", "Definitive alpha", releaseLabel(this.candidate.tag()));
    release.setToolTipText(this.candidate.tag()); release.getAccessibleContext().setAccessibleDescription(this.candidate.tag()); this.body.add(release);
    this.body.add(Box.createVerticalStrut(16)); this.body.add(copy("Your previous version and its pre-update saves and settings remain available in Restore version.", 15, MUTED)); this.body.add(Box.createVerticalStrut(26));
    this.primary("Install update", () -> this.run("Updating Definitive", () -> ReleaseUpdates.install(new InstallStore(this.root), this.candidate, this.currentProgress), () -> {
      this.candidate = null; this.screen = Screen.UPDATED; this.loadInstallationInfo(); this.render(); this.updates.setText("Update installed · Previous version retained");
    }));
    this.body.add(Box.createVerticalStrut(12)); final JPanel actions = new JPanel(new GridLayout(1, 2, 12, 0)); actions.setOpaque(false); actions.setAlignmentX(LEFT_ALIGNMENT); actions.setMaximumSize(new Dimension(520, 48));
    final JButton notes = button("Release notes", false); notes.addActionListener(e -> {
      final JTextArea text = new JTextArea(this.candidate.releaseNotes().isBlank() ? "No release notes were provided for this release." : this.candidate.releaseNotes()); text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true); text.setFont(font(16, false)); text.setCaretPosition(0);
      ManagerDialogs.confirm(this.frame, "Release notes", new JScrollPane(text), "Close");
    }); actions.add(notes);
    final JButton back = button("Back to launcher", false); back.addActionListener(e -> { this.screen = Screen.LAUNCHER; this.render(); }); actions.add(back); this.body.add(actions);
  }
  private void updateFinished() {
    this.heading("Update installed", "Definitive is ready for your next adventure.");
    this.body.add(infoCard("INSTALLATION VERIFIED", "Game and artwork checked", "Your previous version and its pre-update data remain available in Restore version."));
    this.body.add(Box.createVerticalStrut(26)); this.primary("Back to launcher", () -> { this.screen = Screen.LAUNCHER; this.render(); });
  }
  static String releaseLabel(final String tag) {
    final var match = java.util.regex.Pattern.compile("definitive-alpha-(\\d{4}-\\d{2}-\\d{2})(?:-([A-Za-z0-9-]+))?").matcher(tag);
    if(match.matches()) {
      try {
        final String date = java.time.LocalDate.parse(match.group(1)).format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, uuuu", java.util.Locale.US));
        final String edition = match.group(2);
        return date + (edition == null ? "" : " · " + Character.toUpperCase(edition.charAt(0)) + edition.substring(1).replace('-', ' '));
      } catch(final java.time.DateTimeException ignored) { /* Keep an unfamiliar release identifier visible. */ }
    }
    final int count = tag.codePointCount(0, tag.length());
    return count <= 120 ? tag : tag.substring(0, tag.offsetByCodePoints(0, 117)) + "…";
  }
  static String locationLabel(final String location) {
    final int count = location.codePointCount(0, location.length());
    return count <= 48 ? location : location.substring(0, location.offsetByCodePoints(0, 22)) + "…" + location.substring(location.offsetByCodePoints(location.length(), -25));
  }

  private void goBack() {
    if(this.busy) return;
    if(this.failed) { this.render(); return; }
    this.screen = switch(this.screen) {
      case MAINTENANCE, DISCS -> Screen.INSTALL;
      case STEAM -> Screen.DISCS;
      case UPDATE, UPDATED -> this.recoveryPending ? Screen.RECOVERY : Screen.LAUNCHER;
      case STORAGE -> this.recoveryPending ? Screen.RECOVERY : Screen.LAUNCHER;
      default -> this.screen;
    };
    this.render();
  }

  private void finish() {
    this.run("Checking installation", () -> {
      final var store = new InstallStore(this.root); store.verifyInstalled();
      DiscImporter.validateSet(this.root.resolve("isos"));
      if(!store.discsPrepared()) throw new java.io.IOException("Game files are not prepared. Return to the disc step.");
      return "Setup finished at " + this.root;
    }, () -> this.close.run());
  }
  static Path installationPath(final String text) throws java.io.IOException {
    final String value = text.strip();
    final Path path = value.equals("~") ? Path.of(System.getProperty("user.home")) : value.startsWith("~/") ? Path.of(System.getProperty("user.home")).resolve(value.substring(2)) : Path.of(value);
    if(!path.isAbsolute()) throw new java.io.IOException("Choose a full installation path, such as /home/deck/Games/Legend-of-Dragoon-Definitive.");
    return path.normalize();
  }
  boolean isBusy() { return this.busy || this.inspectingIdentity; }
  private void chooseFolder() {
    TouchFilePicker.choose(this.frame, this.root.getParent(), true, paths -> {
      if(paths.isEmpty()) return;
      final Path chosen = paths.getFirst();
      new SwingWorker<Path, Void>() {
        @Override protected Path doInBackground() { return Files.isRegularFile(chosen.resolve("state.properties")) ? chosen : chosen.resolve("Legend-of-Dragoon-Definitive"); }
        @Override protected void done() { try { ManagerView.this.destination.setText(this.get().toString()); ManagerView.this.destination.setCaretPosition(0); } catch(final Exception failure) { ManagerView.this.showFailure(failure); } }
      }.execute();
    });
  }
  private void chooseDiscs() {
    TouchFilePicker.choose(this.frame, Path.of(System.getProperty("user.home")), false, paths -> {
      if(paths.isEmpty()) return;
      this.importDiscs(paths, false);
    });
  }
  private void useInstalledDiscs() {
    if(this.installedDiscs != null && this.installedDiscs.changed() && !ManagerDialogs.confirm(this.frame, "Disc images changed", copy(this.installedDiscs.detail() + " Continue with these images?", 18, INK), "Use these discs")) return;
    this.run("Preparing installed discs", () -> {
      final var store = new InstallStore(this.root);
      return store.prepareInstalledDiscs(this.installedDiscs != null && this.installedDiscs.changed(), this.currentProgress);
    }, () -> { this.screen = Screen.STEAM; this.render(); });
  }
  private void importDiscs(final List<Path> paths, final boolean replaceDifferent) {
      this.run("Preparing your discs", () -> {
        final var store = new InstallStore(this.root);
        store.verifyInstalled();
        try { DiscSources.importSelected(store, paths, this.currentProgress, replaceDifferent); }
        catch(final DiscImporter.DifferentDiscs different) { return different; }
        return store.discsPrepared() ? "Existing game files are ready." : store.prepareDiscs(this.currentProgress);
      }, result -> {
        if(result instanceof DiscImporter.DifferentDiscs different) {
          if(ManagerDialogs.confirm(this.frame, "Different disc images", copy(different.getMessage(), 18, INK), "Replace disc images")) this.importDiscs(paths, true);
        } else { this.screen = Screen.STEAM; this.render(); }
      });
  }
  private void addSteam(final boolean finish) {
    this.run("Checking Steam", () -> SteamLibrary.accounts(), accounts -> {
      if(accounts.isEmpty()) { this.showFailure(new java.io.IOException("No Steam account found. Sign in to Steam once, then retry Add to Steam.")); return; }
      final SteamLibrary.Account selected = accounts.size() == 1 ? accounts.getFirst() : ManagerDialogs.account(this.frame, accounts);
      if(selected == null) return;
      this.run("Adding to Steam", () -> { final var store = new InstallStore(this.root); return store.withOperation(() -> { store.verifyInstalled(); if(!store.discsPrepared()) throw new java.io.IOException("Prepare your game files before adding to Steam."); return SteamIntegration.add(selected, this.root, this.currentProgress); }); }, () -> { if(finish) this.finish(); });
    });
  }
  private void mods() {
    this.run("Loading preferences", () -> new InstallStore(this.root).state(), preferences -> {
      final boolean hd = !"original".equals(preferences.getProperty("artwork", "hd"));
      final JCheckBox artwork = new JCheckBox("Bundled HD artwork & models", hd); artwork.setFont(font(18, false)); artwork.setOpaque(false); artwork.setMaximumSize(new Dimension(520, 52)); artwork.setPreferredSize(new Dimension(520, 52));
      final JCheckBox pilot = new JCheckBox("Enhanced model textures · experimental", false); pilot.setFont(font(18, false)); pilot.setOpaque(false); pilot.setMaximumSize(new Dimension(520, 52)); pilot.setPreferredSize(new Dimension(520, 52));
      final JCheckBox fullscreen = new JCheckBox("Fullscreen", true); fullscreen.setFont(font(18, false)); fullscreen.setOpaque(false); fullscreen.setMaximumSize(new Dimension(520, 52)); fullscreen.setPreferredSize(new Dimension(520, 52));
      fullscreen.setSelected(Boolean.parseBoolean(preferences.getProperty("fullscreen", "true")));
      pilot.setSelected(Boolean.parseBoolean(preferences.getProperty("legacyTextures", "false")));
      final JPanel options = column(); options.add(artwork); options.add(pilot); options.add(fullscreen); options.add(Box.createVerticalStrut(12)); options.add(copy("Includes Skurfa backgrounds and experimental ModelsHD geometry. Enhanced model textures need an installed, verified texture pack.", 16, MUTED));
      if(ManagerDialogs.confirm(this.frame, "Mods & artwork", options, "Save changes")) {
        final boolean hdChoice = artwork.isSelected(), modelChoice = pilot.isSelected(), fullscreenChoice = fullscreen.isSelected();
        this.run("Saving preferences", () -> { new InstallStore(this.root).setPreferences(hdChoice, modelChoice, fullscreenChoice); return "Changes apply the next time you play."; }, () -> { });
      }
    });
  }
  private record InstallationInfo(String label, String full, boolean restorable, boolean identifiable, boolean uninstalled, boolean pending, String notice, String restoredRelease, String restoredData, String preservedLocations) { }
  private void loadInstallationInfo() {
    final long request = ++this.identityRequest;
    this.inspectingIdentity = true;
    new SwingWorker<InstallationInfo, Void>() {
      @Override protected InstallationInfo doInBackground() throws Exception {
        final var store = new InstallStore(ManagerView.this.root); final var state = store.state();
        final boolean uninstalled = Boolean.parseBoolean(state.getProperty("uninstalled", "false"));
        String label = "Retained installation", full = "Package: " + state.getProperty("version", ""); boolean identifiable = false, restorable = false;
        if(!uninstalled) try {
          final var release = InstallStore.child(store.root().resolve("releases"), state.getProperty("version", ""), "alpha-[a-f0-9]{16}");
          final var metadata = PackageManifest.read(release).metadata();
          final String tag = metadata.getProperty("releaseTag", state.getProperty("version", "Installed release"));
          label = releaseLabel(tag); full = "Release: " + tag + " · Source: " + metadata.getProperty("sourceRevision", "unknown") + " · Package: " + state.getProperty("version", ""); identifiable = true; restorable = store.hasRestorableVersion();
        } catch(final java.io.IOException unavailableRelease) { InstallerLog.write("Installed release identity unavailable: " + unavailableRelease.getMessage()); }
        // Recovery is state metadata; it remains reviewable even after an intentional uninstall removed the release.
        return new InstallationInfo(label, full, restorable, identifiable, uninstalled, Boolean.parseBoolean(state.getProperty("recoveryPending", "false")), state.getProperty("recoveryNotice", ""), state.getProperty("recoveryRestoredRelease", ""), state.getProperty("recoveryRestoredData", ""), state.getProperty("recoveryPreservedLocations", ""));
      }
      @Override protected void done() {
        if(request != ManagerView.this.identityRequest) return;
        ManagerView.this.inspectingIdentity = false;
        try { final var info = this.get(); ManagerView.this.installedIdentity = info.label(); ManagerView.this.fullIdentity = info.full(); ManagerView.this.restoreAvailable = info.restorable(); ManagerView.this.identityReady = info.identifiable(); ManagerView.this.recoveryPending = info.pending(); ManagerView.this.recoveryNotice = info.notice(); ManagerView.this.recoveryRestoredRelease = info.restoredRelease(); ManagerView.this.recoveryRestoredData = info.restoredData(); ManagerView.this.recoveryPreservedLocations = info.preservedLocations(); if(info.pending() && ManagerView.this.screen != Screen.STORAGE) ManagerView.this.screen = Screen.RECOVERY; else if(info.uninstalled() && ManagerView.this.screen != Screen.STORAGE) ManagerView.this.screen = ManagerView.this.packageRoot == null ? Screen.UNINSTALLED : Screen.MAINTENANCE; }
        catch(final Exception failure) { ManagerView.this.identityReady = false; ManagerView.this.restoreAvailable = false; InstallerLog.write("Could not inspect installed identity: " + failure.getMessage()); }
        if(!ManagerView.this.busy && !ManagerView.this.failed) { ManagerView.this.render(); if(!ManagerView.this.recoveryPending && ManagerView.this.screen == Screen.DISCS) ManagerView.this.inspectExistingDiscs(); }
      }
    }.execute();
  }
  private void updateStatus() {
    if(!this.screen.launcher() || this.busy || this.failed) return;
    this.updates.setText(this.updateStatus + (this.lastChecked == null ? "" : " · Checked " + java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault()).format(this.lastChecked)));
  }
  private void checkUpdates(final boolean force) {
    if(this.checkingUpdates) return;
    this.checkingUpdates = true; this.updateStatus = "Checking for updates…"; this.updateStatus();
    if(!this.busy && !this.failed) this.render();
    new SwingWorker<java.util.Optional<ReleaseUpdates.Candidate>, Void>() {
      @Override protected java.util.Optional<ReleaseUpdates.Candidate> doInBackground() throws Exception { return ReleaseUpdates.check(new InstallStore(ManagerView.this.root), force); }
      @Override protected void done() {
        ManagerView.this.checkingUpdates = false; ManagerView.this.lastChecked = java.time.Instant.now();
        try { ManagerView.this.candidate = this.get().orElse(null); ManagerView.this.updateStatus = ManagerView.this.candidate == null ? "No update available" : "Update available"; }
        catch(final Exception failure) { ManagerView.this.updateStatus = "Couldn’t check · Retry when connected"; InstallerLog.failure(failure.getCause() == null ? failure : failure.getCause()); }
        if(!ManagerView.this.busy && !ManagerView.this.failed && ManagerView.this.screen == Screen.LAUNCHER) ManagerView.this.render();
        ManagerView.this.updateStatus();
      }
    }.execute();
  }

  private void run(final String working, final Action<String> action, final Runnable done) { this.run(working, action, result -> { this.message(result); done.run(); }); }
  private <T> void run(final String working, final Action<T> action, final java.util.function.Consumer<T> done) {
    if(this.busy || this.inspectingIdentity) return;
    final long ticket = ++this.operation;
    this.busy = true; this.started = System.nanoTime(); this.progressDetail = "Starting…";
    this.progress.setValue(0); this.progress.setString(working); this.progressPhase.setText("Starting…"); this.progressPercent.setText("0%"); this.progress.setVisible(true); this.progressPanel.setVisible(!working.equals("Game running"));
    this.body.removeAll(); if(!this.screen.launcher()) this.stages(); this.heading(working, working.equals("Game running") ? "Close the game to return to the launcher." : "Keep this window open while this step finishes.");
    this.body.revalidate(); this.body.repaint();
    if(this.progressPanel.isVisible()) { this.progressStatus(); this.elapsed.start(); }
    else this.updates.setText("Game running · Close it to return here");
    InstallerLog.write(working + " · " + this.root);
    new SwingWorker<T, InstallProgress.Update>() {
      private volatile long lastReport;
      private volatile String lastPhase = "";
      @Override protected T doInBackground() throws Exception {
        ManagerView.this.currentProgress = update -> {
          final long now = System.nanoTime();
          if(!update.phase().equals(this.lastPhase) || now - this.lastReport > 150_000_000L || update.percent() == 100) {
            if(!update.phase().equals(this.lastPhase)) InstallerLog.write(update.phase() + ": " + update.detail());
            this.lastPhase = update.phase(); this.lastReport = now; this.publish(update);
          }
        };
        return action.run();
      }
      @Override protected void process(final List<InstallProgress.Update> updates) {
        if(updates.isEmpty() || ticket != ManagerView.this.operation || !ManagerView.this.busy) return;
        final var update = updates.getLast();
        ManagerView.this.progress.setValue(Math.max(ManagerView.this.progress.getValue(), update.percent()));
        ManagerView.this.progress.setString(update.phase());
        ManagerView.this.progressPhase.setText(update.phase()); ManagerView.this.progressPercent.setText(ManagerView.this.progress.getValue() + "%");
        ManagerView.this.progressDetail = update.detail(); ManagerView.this.progressStatus();
      }
      @Override protected void done() {
        ManagerView.this.busy = false; ManagerView.this.elapsed.stop(); ManagerView.this.currentProgress = InstallProgress.NONE;
        ManagerView.this.progress.setVisible(false); ManagerView.this.progressPanel.setVisible(false);
        try {
          final T result = this.get();
          InstallerLog.write(working + " completed: " + result);
          ManagerView.this.render(); done.accept(result);
        } catch(final Exception failure) { ManagerView.this.showFailure(failure.getCause() != null ? failure.getCause() : failure); }
      }
    }.execute();
  }
  private void progressStatus() {
    final long seconds = Math.max(0, (System.nanoTime() - this.started) / 1_000_000_000L);
    // Keep a changing timer from reflowing file paths or crowding out the work detail.
    this.message(progressSummary(this.progressDetail));
    this.status.setToolTipText(this.progressDetail);
    this.status.getAccessibleContext().setAccessibleDescription(this.progressDetail);
    this.progressElapsed.setText("Elapsed " + seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60));
  }
  private static String progressSummary(final String detail) {
    if(detail.codePointCount(0, detail.length()) <= 96) return detail;
    final int limit = detail.offsetByCodePoints(0, 95);
    final int space = detail.lastIndexOf(' ', limit);
    return detail.substring(0, space >= detail.offsetByCodePoints(0, 64) ? space : limit).stripTrailing() + "…";
  }
  void showFailure(final Throwable failure) {
    this.failed = true;
    InstallerLog.failure(failure);
    this.body.removeAll();
    this.progressPanel.setVisible(false);
    this.heading(this.screen.launcher() ? "Couldn’t finish this step" : "Setup stopped", "Review the error, then return to this step to try again.");
    final String reason = failure.getMessage() == null ? "See the log for details." : failure.getMessage();
    this.body.add(infoCard("WHAT HAPPENED", "This step didn’t complete", reason.length() > 180 ? reason.substring(0, 177) + "…" : reason)); this.body.add(Box.createVerticalStrut(22));
    this.primary(this.screen.launcher() ? this.screen == Screen.UPDATE ? "Back to update" : this.screen == Screen.STORAGE ? "Back to storage" : this.screen == Screen.RECOVERY ? "Back to recovery" : "Back to launcher" : "Back to setup", () -> this.render()); this.body.add(Box.createVerticalStrut(12));
    final JButton details = button("Show error details", false);
    details.addActionListener(e -> new SwingWorker<String, Void>() {
      @Override protected String doInBackground() { return DiagnosticsReport.collect(ManagerView.this.root); }
      @Override protected void done() {
        try {
          final String report = this.get(); final JTextArea area = new JTextArea(report); area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true); area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14)); area.setCaretPosition(0);
          if(ManagerDialogs.confirm(ManagerView.this.frame, "Error details", new JScrollPane(area), "Export report")) TouchFilePicker.choose(ManagerView.this.frame, Path.of(System.getProperty("user.home")), true, paths -> {
            if(!paths.isEmpty()) ManagerView.this.run("Exporting diagnostics", () -> "Report saved: " + DiagnosticsReport.export(paths.getFirst(), report), () -> { });
          });
        } catch(final Exception problem) { InstallerLog.failure(problem); ManagerView.this.message("Could not load diagnostics: " + problem.getMessage()); }
      }
    }.execute());
    this.body.add(details); this.updates.setText("Show error details for the full log");
    this.message("Retry after addressing the error above."); this.body.revalidate(); this.body.repaint(); this.focusPrimaryAction();
  }
  private void message(final String text) {
    this.status.setText("<html><div style='width:330px'>" + escape(text, 15, 330) + "</div></html>");
    if((!this.busy || !this.progressPanel.isVisible()) && !this.failed) this.updates.setText("<html><div style='width:360px'>" + escape(text.length() > 120 ? text.substring(0, 117) + "…" : text, 14, 360) + "</div></html>");
  }
  @FunctionalInterface private interface Action<T> { T run() throws Exception; }
  private JButton primary(final String title, final Runnable action) { final JButton button = button(title, true); button.addActionListener(e -> action.run()); this.body.add(button); if(this.frame != null) this.frame.getRootPane().setDefaultButton(button); return button; }
  private static JPanel column() { final JPanel p = new JPanel(); p.setOpaque(false); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); p.setAlignmentX(LEFT_ALIGNMENT); return p; }
  static Font font(final int size, final boolean bold) { return new Font(UI_FONT, bold ? Font.BOLD : Font.PLAIN, size); }
  private static JLabel label(final String text, final int size, final Color colour) { final JLabel l = new JLabel(text); l.setFont(font(size, false)); l.setForeground(colour); l.setAlignmentX(LEFT_ALIGNMENT); return l; }
  static JLabel copy(final String text, final int size, final Color colour) { return label("<html><div style='width:360px'>" + escape(text, size, 360) + "</div></html>", size, colour); }
  private static String escape(final String text, final int size, final int width) {
    // Swing's HTML renderer does not wrap a long filesystem token. Insert explicit
    // line breaks before escaping each segment so paths cannot hide UI content.
    final FontMetrics metrics = new JLabel().getFontMetrics(font(size, true));
    final String wrapped = java.util.regex.Pattern.compile("\\S+").matcher(text).replaceAll(match -> {
      String token = match.group(); final var lines = new StringBuilder();
      while(token.codePointCount(0, token.length()) > 44 || metrics.stringWidth(token) > width) {
        int count = Math.min(44, token.codePointCount(0, token.length()));
        while(count > 1 && metrics.stringWidth(token.substring(0, token.offsetByCodePoints(0, count))) > width) count--;
        final int limit = token.offsetByCodePoints(0, count), minimum = token.offsetByCodePoints(0, Math.min(12, count));
        int split = token.lastIndexOf('/', limit - 1) + 1;
        if(split < minimum) split = Math.max(token.lastIndexOf('-', limit - 1), token.lastIndexOf('_', limit - 1)) + 1;
        if(split < minimum) split = limit;
        if(token.codePointCount(split, token.length()) < 8 && token.codePointCount(0, token.length()) > 8) split = token.offsetByCodePoints(token.length(), -8);
        lines.append(token, 0, split).append('\n'); token = token.substring(split);
      }
      return java.util.regex.Matcher.quoteReplacement(lines.append(token).toString());
    });
    return wrapped.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
  }
  static javax.swing.border.Border roundedBorder() {
    return new javax.swing.border.AbstractBorder() {
      @Override public Insets getBorderInsets(final Component component) { return new Insets(1, 1, 1, 1); }
      @Override public void paintBorder(final Component component, final Graphics graphics, final int x, final int y, final int width, final int height) {
        final Graphics2D g = (Graphics2D)graphics.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if(component.isFocusOwner() && component.isEnabled()) { g.setColor(GREEN); g.setStroke(new BasicStroke(2)); g.drawRoundRect(x + 1, y + 1, width - 3, height - 3, 16, 16); }
        else { g.setColor(LINE); g.drawRoundRect(x, y, width - 1, height - 1, 16, 16); }
        g.dispose();
      }
    };
  }
  private static JPanel card() {
    final JPanel panel = new JPanel() {
      @Override public Dimension getMaximumSize() { return new Dimension(520, this.getPreferredSize().height); }
      @Override protected void paintComponent(final Graphics graphics) {
        final Graphics2D g = (Graphics2D)graphics.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g.setColor(new Color(0xffffff)); g.fillRoundRect(0, 0, this.getWidth(), this.getHeight(), 18, 18); g.dispose(); super.paintComponent(graphics);
      }
    };
    panel.setOpaque(false); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); panel.setAlignmentX(LEFT_ALIGNMENT);
    panel.setBorder(BorderFactory.createCompoundBorder(roundedBorder(), BorderFactory.createEmptyBorder(20, 20, 20, 20))); panel.setMaximumSize(new Dimension(520, 180)); return panel;
  }
  private static JPanel infoCard(final String caption, final String title, final String detail) {
    final JPanel panel = card(); panel.add(label(caption, 12, MUTED)); panel.add(Box.createVerticalStrut(10));
    final JLabel name = copy(title, 19, INK); name.setFont(font(19, true)); panel.add(name); panel.add(Box.createVerticalStrut(8));
    panel.add(label("<html><div style='width:330px'>" + escape(detail, 15, 330) + "</div></html>", 15, MUTED)); return panel;
  }
  static JButton button(final String text, final boolean primary) {
    final JButton b = new JButton(text) {
      @Override protected void paintComponent(final Graphics graphics) {
        final Graphics2D g = (Graphics2D)graphics.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(primary ? (this.getModel().isPressed() ? new Color(0x183d30) : this.getModel().isRollover() ? new Color(0x326450) : GREEN) : (this.getModel().isPressed() ? new Color(0xdde4d9) : this.getModel().isRollover() ? new Color(0xe6e8df) : new Color(0xeceee7)));
        if(!this.isEnabled()) g.setColor(new Color(0xd9ddd4));
        g.fillRoundRect(0, 0, this.getWidth(), this.getHeight(), 18, 18);
        if(this.isFocusOwner() && this.isEnabled()) { g.setColor(primary ? new Color(0xcbe3d4) : GREEN); g.setStroke(new BasicStroke(2)); g.drawRoundRect(2, 2, this.getWidth() - 5, this.getHeight() - 5, 16, 16); }
        g.dispose(); super.paintComponent(graphics);
      }
    };
    b.setFont(font(primary ? 21 : 16, primary)); b.setForeground(primary ? new Color(0xffffff) : GREEN); b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false); b.setRolloverEnabled(true); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); b.setAlignmentX(LEFT_ALIGNMENT); b.setMaximumSize(new Dimension(520, primary ? 60 : 48)); b.setPreferredSize(new Dimension(460, primary ? 60 : 48)); b.setMinimumSize(new Dimension(100, primary ? 60 : 48)); return b;
  }
  private final class Hero extends JPanel {
    private BufferedImage image;
    Hero() { try(final var in = ManagerView.class.getResourceAsStream("hero.png")) { if(in != null) this.image = ImageIO.read(in); } catch(final Exception ignored) { /* Branding stays readable when preview art is unavailable. */ } }
    @Override public Dimension getPreferredSize() { return new Dimension(Math.max(340, Math.min(440, (int)(ManagerView.this.getWidth() * .36))), 700); }
    @Override protected void paintComponent(final Graphics graphics) {
      final Graphics2D g = (Graphics2D)graphics.create(); final int w = this.getWidth(), h = this.getHeight();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setColor(new Color(0x192822)); g.fillRect(0, 0, w, h);
      if(this.image != null) { final double scale = Math.max((double)w / this.image.getWidth(), (double)h / this.image.getHeight()); final int iw = (int)(this.image.getWidth() * scale), ih = (int)(this.image.getHeight() * scale); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC); g.drawImage(this.image, (w - iw) / 2, (h - ih) / 2, iw, ih, null); }
      g.setPaint(new LinearGradientPaint(0, 0, 0, h, new float[]{0, 0.35f, 0.6f, 1}, new Color[]{new Color(10, 24, 20, 210), new Color(10, 24, 20, 70), new Color(10, 24, 20, 110), new Color(10, 24, 20, 245)})); g.fillRect(0, 0, w, h);
      final Font serif = new Font(java.util.Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()).contains("Baskerville") ? "Baskerville" : Font.SERIF, Font.PLAIN, 34);
      g.setColor(new Color(0xf6efd9)); g.setFont(font(12, false).deriveFont(java.util.Map.of(java.awt.font.TextAttribute.TRACKING, .23f))); g.drawString("THE LEGEND OF", 42, 62);
      g.setFont(serif.deriveFont(48f)); g.drawString("Dragoon", 39, 111);
      g.setColor(new Color(0xd6c59b)); g.setFont(font(12, true).deriveFont(java.util.Map.of(java.awt.font.TextAttribute.TRACKING, .23f))); g.drawString("DEFINITIVE", 42, 144);
      g.setColor(new Color(0xf6efd9)); g.setFont(serif.deriveFont(38f)); g.drawString("A legend.", 40, h - 178); g.drawString("Reawakened.", 40, h - 134);
      g.setColor(new Color(0xd5d6c6)); g.setFont(font(14, false)); g.drawString("The original adventure. A new edition.", 42, h - 102);
      g.setColor(new Color(0xadaf9d)); g.setFont(font(12, false)); g.drawString("HD artwork by Skurfa", 42, h - 36); g.dispose();
    }
  }
}
