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
  private int step;
  private boolean launcher;
  private boolean reviewingUpdate;
  private boolean updateComplete;
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
    this.launcher = packageRoot == null && Files.isRegularFile(root.resolve("state.properties"));
    if(this.launcher) {
      try { DiscImporter.validateSet(root.resolve("isos")); if(!new InstallStore(root).discsPrepared()) { this.launcher = false; this.step = 1; } }
      catch(final Exception e) { this.launcher = false; this.step = 1; }
    }
    this.destination = new JTextField(root.toString()); this.destination.setFont(font(15, false)); this.destination.setAlignmentX(LEFT_ALIGNMENT);
    this.destination.setBackground(Color.WHITE); this.destination.setForeground(INK); this.destination.setBorder(BorderFactory.createCompoundBorder(roundedBorder(), BorderFactory.createEmptyBorder(12, 14, 12, 14)));
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
    content.add(this.progressPanel); content.add(Box.createVerticalGlue());
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
      if(this.launcher) this.checkUpdates();
    }
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
    if(this.launcher && this.updateComplete) this.updateFinished();
    else if(this.launcher && this.reviewingUpdate && this.candidate != null) this.updateScreen();
    else if(this.launcher) this.launcher(); else this.setup();
    this.body.revalidate(); this.body.repaint();
    SwingUtilities.invokeLater(() -> { if(this.frame != null && this.frame.getRootPane().getDefaultButton() != null) this.frame.getRootPane().getDefaultButton().requestFocusInWindow(); });
  }
  private void heading(final String title, final String description) {
    final JLabel heading = copy(title, 34, INK); heading.setFont(font(34, true)); this.body.add(heading); this.body.add(Box.createVerticalStrut(12));
    this.body.add(copy(description, 17, MUTED)); this.body.add(Box.createVerticalStrut(26));
  }
  private void setup() {
    this.updates.setText("Community alpha · Built on Severed Chains");
    this.stages();
    switch(this.step) {
      case 0 -> {
        this.heading("Install Definitive", "Set up the game, HD artwork and launcher.");
        this.body.add(label("Install location", 13, MUTED)); this.body.add(Box.createVerticalStrut(8));
        this.destination.setMaximumSize(new Dimension(520, 48)); this.destination.setCaretPosition(0); this.body.add(this.destination); this.body.add(Box.createVerticalStrut(8));
        final JButton browse = button("Choose folder", false); browse.addActionListener(e -> this.chooseFolder()); this.body.add(browse); this.body.add(Box.createVerticalStrut(20));
        this.body.add(copy("Next, you’ll select your disc images. Your originals stay where they are.", 15, MUTED)); this.body.add(Box.createVerticalStrut(24));
        this.primary("Install Definitive", () -> {
          try { this.root = installationPath(this.destination.getText()); this.destination.setText(this.root.toString()); }
          catch(final Exception e) { this.showFailure(e); return; }
          this.run("Installing Definitive", () -> {
            final var store = new InstallStore(this.root);
            final String result = PortableSetup.install(this.packageRoot, store, this.currentProgress);
            store.verifyInstalled();
            return result;
          }, () -> { this.step = 1; this.render(); });
        });
      }
      case 1 -> {
        this.heading("Select your discs", "Choose the four US disc images, or archives containing them.");
        this.body.add(infoCard("SUPPORTED FILES", "BIN / raw ISO · ZIP · RAR · 7z", "Your files are checked and copied before the game is prepared."));
        this.body.add(Box.createVerticalStrut(28));
        this.primary("Choose disc files", () -> this.chooseDiscs());
        this.body.add(Box.createVerticalStrut(12));
        final JButton existing = button("Use installed discs", false); existing.addActionListener(e -> this.run("Preparing game files", () -> new InstallStore(this.root).prepareDiscs(this.currentProgress), () -> { this.step = 2; this.render(); }));
        this.setupActions(existing);
      }
      default -> {
        this.heading("You’re ready to play", "Add Definitive to Steam for Gaming Mode, or finish setup.");
        this.body.add(infoCard("INSTALLATION VERIFIED", "Game and HD artwork ready", this.root.toString()));
        this.body.add(Box.createVerticalStrut(12)); this.body.add(copy("Steam restarts to refresh your library. Your shortcuts are backed up.", 15, MUTED));
        this.body.add(Box.createVerticalStrut(28));
        this.primary("Add to Steam", () -> this.addSteam(true));
        this.body.add(Box.createVerticalStrut(12));
        final JButton skip = button("Finish without Steam", false); skip.addActionListener(e -> this.finish()); this.setupActions(skip);
      }
    }
  }
  private void setupActions(final JButton secondary) {
    final JPanel actions = new JPanel(new GridLayout(1, 2, 12, 0)); actions.setOpaque(false); actions.setAlignmentX(LEFT_ALIGNMENT); actions.setMaximumSize(new Dimension(520, 48));
    final JButton back = button("Back", false); back.addActionListener(e -> this.goBack()); actions.add(secondary); actions.add(back); this.body.add(actions);
  }
  private void launcher() {
    this.heading("Ready to play", "The Legend of Dragoon · Definitive");
    this.body.add(infoCard("YOUR EDITION", "HD backgrounds. Refined menus.", "Choose Faithful or Definitive when you start a new campaign."));
    this.body.add(Box.createVerticalStrut(30));
    this.primary("Play", () -> this.run("Game running", () -> {
      final var store = new InstallStore(this.root); final int code = store.play();
      if(code != 0) throw new java.io.IOException("The game exited with code " + code + ". Details: " + store.gameLog());
      return "Game closed.";
    }, () -> { }));
    this.body.add(Box.createVerticalStrut(16));
    final JPanel secondary = new JPanel(new GridLayout(1, 2, 12, 0)); secondary.setOpaque(false); secondary.setAlignmentX(LEFT_ALIGNMENT); secondary.setMaximumSize(new Dimension(520, 48));
    final JButton mods = button("Mods & artwork", false); mods.addActionListener(e -> this.mods());
    final JButton restore = button("Restore version", false); restore.addActionListener(e -> {
      if(ManagerDialogs.restore(this.frame)) this.run("Restoring version", () -> new InstallStore(this.root).rollback(), () -> { this.candidate = null; this.render(); });
    });
    secondary.add(mods); secondary.add(restore); this.body.add(secondary);
    this.body.add(Box.createVerticalStrut(20));
    final JButton steam = button("Add to Steam library", false); steam.addActionListener(e -> this.addSteam(false)); this.body.add(steam);
    if(this.candidate != null) {
      this.body.add(Box.createVerticalStrut(12)); final JButton update = button("Review update", false);
      update.addActionListener(e -> { this.reviewingUpdate = true; this.render(); }); this.body.add(update);
    }
  }
  private void stages() {
    final JPanel stages = new JPanel(new GridLayout(1, 3, 12, 0)); stages.setOpaque(false); stages.setAlignmentX(LEFT_ALIGNMENT);
    final String[] names = {"01   Install", "02   Your discs", "03   Steam"};
    for(int i = 0; i < 3; i++) { final JLabel item = label(names[i], 14, i == this.step ? GREEN : MUTED); item.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, i == this.step ? GREEN : new Color(0xdedfd7))); stages.add(item); }
    stages.setMaximumSize(new Dimension(520, 32)); this.body.add(stages); this.body.add(Box.createVerticalStrut(32));
  }
  private void updateScreen() {
    this.heading("Update Definitive", "Install the latest release, with your saves and settings kept.");
    this.body.add(infoCard("AVAILABLE RELEASE", "Definitive alpha", this.candidate.tag().length() > 120 ? this.candidate.tag().substring(0, 117) + "…" : this.candidate.tag()));
    this.body.add(Box.createVerticalStrut(16)); this.body.add(copy("Your previous version and its pre-update saves and settings remain available in Restore version.", 15, MUTED)); this.body.add(Box.createVerticalStrut(26));
    this.primary("Install update", () -> this.run("Updating Definitive", () -> ReleaseUpdates.install(new InstallStore(this.root), this.candidate, this.currentProgress), () -> {
      this.candidate = null; this.reviewingUpdate = false; this.updateComplete = true; this.render(); this.updates.setText("Update installed · Previous version retained");
    }));
    this.body.add(Box.createVerticalStrut(12)); final JButton back = button("Back to launcher", false);
    back.addActionListener(e -> { this.reviewingUpdate = false; this.render(); }); this.body.add(back);
  }
  private void updateFinished() {
    this.heading("Update installed", "Definitive is ready for your next adventure.");
    this.body.add(infoCard("INSTALLATION VERIFIED", "Game and artwork checked", "Your previous version and its pre-update data remain available in Restore version."));
    this.body.add(Box.createVerticalStrut(26)); this.primary("Back to launcher", () -> { this.updateComplete = false; this.render(); });
  }

  private void goBack() {
    if(this.busy) return;
    if(this.failed) { this.render(); return; }
    if(this.launcher && (this.reviewingUpdate || this.updateComplete)) {
      this.reviewingUpdate = false; this.updateComplete = false; this.render();
    } else if(!this.launcher && this.step > 0) { this.step--; this.render(); }
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
  boolean isBusy() { return this.busy; }
  private void chooseFolder() {
    TouchFilePicker.choose(this.frame, this.root.getParent(), true, paths -> {
      if(paths.isEmpty()) return;
      final Path chosen = paths.getFirst();
      this.destination.setText((Files.isRegularFile(chosen.resolve("state.properties")) ? chosen : chosen.resolve("Legend-of-Dragoon-Definitive")).toString());
      this.destination.setCaretPosition(0);
    });
  }
  private void chooseDiscs() {
    TouchFilePicker.choose(this.frame, Path.of(System.getProperty("user.home")), false, paths -> {
      if(paths.isEmpty()) return;
      this.run("Preparing your discs", () -> {
        final var store = new InstallStore(this.root);
        store.verifyInstalled();
        DiscSources.importSelected(store, paths, this.currentProgress);
        return store.prepareDiscs(this.currentProgress);
      }, () -> { this.step = 2; this.render(); });
    });
  }
  private void addSteam(final boolean finish) {
    this.run("Checking Steam", () -> SteamLibrary.accounts(), accounts -> {
      if(accounts.isEmpty()) { this.showFailure(new java.io.IOException("No Steam account found. Sign in to Steam once, then retry Add to Steam.")); return; }
      final SteamLibrary.Account selected = accounts.size() == 1 ? accounts.getFirst() : ManagerDialogs.account(this.frame, accounts);
      if(selected == null) return;
      this.run("Adding to Steam", () -> { final var store = new InstallStore(this.root); store.verifyInstalled(); if(!store.discsPrepared()) throw new java.io.IOException("Prepare your game files before adding to Steam."); return SteamIntegration.add(selected, this.root, this.currentProgress); }, () -> { if(finish) this.finish(); });
    });
  }
  private void mods() {
    this.run("Loading preferences", () -> !"original".equals(new InstallStore(this.root).state().getProperty("artwork", "hd")), hd -> {
      final JCheckBox artwork = new JCheckBox("Skurfa HD backgrounds", hd); artwork.setFont(font(18, false)); artwork.setOpaque(false); artwork.setMaximumSize(new Dimension(520, 52)); artwork.setPreferredSize(new Dimension(520, 52));
      final JCheckBox pilot = new JCheckBox("Enhanced model textures · experimental", false); pilot.setFont(font(18, false)); pilot.setOpaque(false); pilot.setMaximumSize(new Dimension(520, 52)); pilot.setPreferredSize(new Dimension(520, 52));
      try { pilot.setSelected(Boolean.parseBoolean(new InstallStore(this.root).state().getProperty("legacyTextures", "false"))); } catch(final Exception ignored) { }
      final JPanel options = column(); options.add(artwork); options.add(pilot); options.add(Box.createVerticalStrut(12)); options.add(copy("Artwork doesn’t change gameplay. Enhanced model textures need an installed, verified texture pack.", 16, MUTED));
      if(ManagerDialogs.confirm(this.frame, "Mods & artwork", options, "Save changes")) this.run("Saving preferences", () -> { new InstallStore(this.root).setArtwork(artwork.isSelected()); new InstallStore(this.root).setLegacyTextures(pilot.isSelected()); return "Changes apply the next time you play."; }, () -> { });
    });
  }
  private void checkUpdates() {
    this.updates.setText("Checking for updates…");
    new SwingWorker<java.util.Optional<ReleaseUpdates.Candidate>, Void>() {
      @Override protected java.util.Optional<ReleaseUpdates.Candidate> doInBackground() throws Exception { return ReleaseUpdates.check(new InstallStore(ManagerView.this.root)); }
      @Override protected void done() {
        try { ManagerView.this.candidate = this.get().orElse(null); ManagerView.this.updates.setText(ManagerView.this.candidate == null ? "No update available" : "Update available"); if(!ManagerView.this.busy && !ManagerView.this.failed) ManagerView.this.render(); }
        catch(final Exception e) { ManagerView.this.updates.setText("Couldn’t check for updates"); }
      }
    }.execute();
  }
  private void run(final String working, final Action<String> action, final Runnable done) { this.run(working, action, result -> { this.message(result); done.run(); }); }
  private <T> void run(final String working, final Action<T> action, final java.util.function.Consumer<T> done) {
    if(this.busy) return;
    final long ticket = ++this.operation;
    this.busy = true; this.started = System.nanoTime(); this.progressDetail = "Starting…";
    this.progress.setValue(0); this.progress.setString(working); this.progressPhase.setText("Starting…"); this.progressPercent.setText("0%"); this.progress.setVisible(true); this.progressPanel.setVisible(!working.equals("Game running"));
    this.body.removeAll(); if(!this.launcher) this.stages(); this.heading(working, working.equals("Game running") ? "Close the game to return to the launcher." : "Keep this window open while this step finishes.");
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
    this.heading(this.launcher ? "Couldn’t finish this step" : "Setup stopped", "Review the error, then return to this step to try again.");
    final String reason = failure.getMessage() == null ? "See the log for details." : failure.getMessage();
    this.body.add(infoCard("WHAT HAPPENED", "This step didn’t complete", reason.length() > 180 ? reason.substring(0, 177) + "…" : reason)); this.body.add(Box.createVerticalStrut(22));
    this.primary(this.launcher ? this.reviewingUpdate ? "Back to update" : "Back to launcher" : "Back to setup", () -> this.render()); this.body.add(Box.createVerticalStrut(12));
    final JButton details = button("Show error details", false);
    details.addActionListener(e -> {
      final var area = new JTextArea(); area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true); area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
      try { area.setText("Log: " + InstallerLog.path() + "\n\n" + InstallerLog.tail()); }
      catch(final Exception e1) { area.setText("Could not read the log: " + e1.getMessage()); }
      ManagerDialogs.confirm(this.frame, "Installer log", new JScrollPane(area), "Close");
    });
    this.body.add(details); this.updates.setText("Show error details for the full log");
    this.message("Retry after addressing the error above."); this.body.revalidate(); this.body.repaint();
  }
  private void message(final String text) {
    this.status.setText("<html><div style='width:330px'>" + escape(text) + "</div></html>");
    if((!this.busy || !this.progressPanel.isVisible()) && !this.failed) this.updates.setText("<html><div style='width:360px'>" + escape(text.length() > 120 ? text.substring(0, 117) + "…" : text) + "</div></html>");
  }
  @FunctionalInterface private interface Action<T> { T run() throws Exception; }
  private void primary(final String title, final Runnable action) { final JButton button = button(title, true); button.addActionListener(e -> action.run()); this.body.add(button); if(this.frame != null) this.frame.getRootPane().setDefaultButton(button); }
  private static JPanel column() { final JPanel p = new JPanel(); p.setOpaque(false); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); p.setAlignmentX(LEFT_ALIGNMENT); return p; }
  static Font font(final int size, final boolean bold) { return new Font(UI_FONT, bold ? Font.BOLD : Font.PLAIN, size); }
  private static JLabel label(final String text, final int size, final Color colour) { final JLabel l = new JLabel(text); l.setFont(font(size, false)); l.setForeground(colour); l.setAlignmentX(LEFT_ALIGNMENT); return l; }
  static JLabel copy(final String text, final int size, final Color colour) { return label("<html><div style='width:360px'>" + escape(text) + "</div></html>", size, colour); }
  private static String escape(final String text) {
    // Swing's HTML renderer does not wrap a long filesystem token. Insert explicit
    // line breaks before escaping each segment so paths cannot hide UI content.
    final String wrapped = java.util.regex.Pattern.compile("\\S{45,}").matcher(text).replaceAll(match -> {
      String token = match.group(); final var lines = new StringBuilder();
      while(token.length() > 44) {
        int split = token.lastIndexOf('/', 43) + 1;
        if(split < 12) split = Math.max(token.lastIndexOf('-', 43), token.lastIndexOf('_', 43)) + 1;
        if(split < 12) split = 44;
        if(token.length() - split < 8) split = token.length() - 8;
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
        final Graphics2D g = (Graphics2D)graphics.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g.setColor(LINE); g.drawRoundRect(x, y, width - 1, height - 1, 16, 16); g.dispose();
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
    final JLabel name = label(title, 19, INK); name.setFont(font(19, true)); panel.add(name); panel.add(Box.createVerticalStrut(8));
    panel.add(label("<html><div style='width:330px'>" + escape(detail) + "</div></html>", 15, MUTED)); return panel;
  }
  static JButton button(final String text, final boolean primary) {
    final JButton b = new JButton(text) {
      @Override protected void paintComponent(final Graphics graphics) {
        final Graphics2D g = (Graphics2D)graphics.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(primary ? (this.getModel().isPressed() ? new Color(0x183d30) : this.getModel().isRollover() ? new Color(0x326450) : GREEN) : (this.getModel().isPressed() ? new Color(0xdde4d9) : this.getModel().isRollover() ? new Color(0xe6e8df) : new Color(0xeceee7)));
        if(!this.isEnabled()) g.setColor(new Color(0xd9ddd4));
        g.fillRoundRect(0, 0, this.getWidth(), this.getHeight(), 18, 18);
        if(this.isFocusOwner()) { g.setColor(new Color(0x8eac9c)); g.setStroke(new BasicStroke(2)); g.drawRoundRect(2, 2, this.getWidth() - 5, this.getHeight() - 5, 16, 16); }
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
