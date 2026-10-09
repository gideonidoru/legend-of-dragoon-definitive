// Definitive guided setup and launcher (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;

/** One action per setup step; a Play-first launcher after setup. Backend work stays off the UI thread. */
final class ManagerView extends JPanel {
  static final Color PAPER = new Color(0xf6f4ef), INK = new Color(0x202b28), MUTED = new Color(0x68716b), GREEN = new Color(0x244e40);
  private final JFrame frame;
  private final Path packageRoot;
  private Path root;
  private final JPanel body = column();
  private final JLabel status = label("", 15, MUTED);
  private final JLabel updates = label("", 14, MUTED);
  private final JProgressBar progress = new JProgressBar();
  private final JTextField destination;
  private boolean busy;
  private int step;
  private boolean launcher;
  private ReleaseUpdates.Candidate candidate;

  ManagerView(final JFrame frame, final Path packageRoot, final Path root) {
    this.frame = frame; this.packageRoot = packageRoot; this.root = root;
    this.launcher = packageRoot == null && Files.isRegularFile(root.resolve("state.properties"));
    if(this.launcher) {
      try { DiscImporter.validateSet(root.resolve("isos")); }
      catch(final Exception e) { this.launcher = false; this.step = 1; }
    }
    this.destination = new JTextField(root.toString()); this.destination.setFont(font(15, false)); this.destination.setAlignmentX(LEFT_ALIGNMENT);
    this.destination.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xdedfd7)), BorderFactory.createEmptyBorder(12, 12, 12, 12)));
    this.setLayout(new BorderLayout()); this.setBackground(PAPER);
    this.add(new Hero(), BorderLayout.WEST);
    final JPanel content = column(); content.setBorder(BorderFactory.createEmptyBorder(38, 48, 30, 48));
    final JLabel eyebrow = label("DEFINITIVE EDITION", 13, GREEN); eyebrow.setFont(font(13, true).deriveFont(java.util.Map.of(java.awt.font.TextAttribute.TRACKING, 0.16f)));
    content.add(eyebrow); content.add(Box.createVerticalStrut(28)); content.add(this.body);
    content.add(Box.createVerticalGlue());
    this.progress.setIndeterminate(true); this.progress.setVisible(false); this.progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4)); this.progress.setForeground(GREEN); this.progress.setBorderPainted(false);
    content.add(this.progress); content.add(Box.createVerticalStrut(14));
    this.status.setMaximumSize(new Dimension(Integer.MAX_VALUE, 75)); content.add(this.status);
    content.add(Box.createVerticalStrut(16)); content.add(this.updates);
    content.add(Box.createVerticalStrut(12)); content.add(label("D-pad / stick  Navigate     A  Select     B  Back", 13, MUTED));
    this.add(content, BorderLayout.CENTER);
    if(frame != null) {
      frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "back");
      frame.getRootPane().getActionMap().put("back", new AbstractAction() { @Override public void actionPerformed(final ActionEvent e) { if(!ManagerView.this.busy && !ManagerView.this.launcher && ManagerView.this.step > 0) { ManagerView.this.step--; ManagerView.this.render(); } } });
    }
    this.render();
    if(frame != null) {
      frame.addWindowListener(new WindowAdapter() { @Override public void windowClosing(final WindowEvent e) { if(ManagerView.this.busy) ManagerView.this.message("Please wait for this operation to finish, or close the game first."); else frame.dispose(); } });
      if(this.launcher) this.checkUpdates();
    }
  }

  private void render() {
    this.body.removeAll();
    if(this.launcher) this.launcher(); else this.setup();
    if(!this.launcher && this.step > 0) {
      this.body.add(Box.createVerticalStrut(12)); final JButton back = button("←  Back", false);
      back.addActionListener(e -> { this.step--; this.render(); }); this.body.add(back);
    }
    this.body.revalidate(); this.body.repaint();
    SwingUtilities.invokeLater(() -> { if(this.frame != null && this.frame.getRootPane().getDefaultButton() != null) this.frame.getRootPane().getDefaultButton().requestFocusInWindow(); });
  }
  private void heading(final String title, final String description) {
    this.body.add(label(title, 38, INK)); this.body.add(Box.createVerticalStrut(14));
    this.body.add(copy(description, 18, MUTED)); this.body.add(Box.createVerticalStrut(28));
  }
  private void setup() {
    this.updates.setText("Built on Severed Chains · Community made · Alpha");
    final JPanel stages = new JPanel(new GridLayout(1, 3, 12, 0)); stages.setOpaque(false); stages.setAlignmentX(LEFT_ALIGNMENT);
    final String[] names = {"01   Install", "02   Your discs", "03   Steam"};
    for(int i = 0; i < 3; i++) { final JLabel item = label(names[i], 14, i == this.step ? GREEN : MUTED); item.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, i == this.step ? GREEN : new Color(0xdedfd7))); stages.add(item); }
    stages.setMaximumSize(new Dimension(520, 32)); this.body.add(stages); this.body.add(Box.createVerticalStrut(32));
    switch(this.step) {
      case 0 -> {
        this.heading("Your journey starts here.", "A considered new home for a timeless adventure. We’ll install Definitive and its HD artwork, then bring in your discs.");
        this.body.add(label("INSTALLATION FOLDER", 12, MUTED)); this.body.add(Box.createVerticalStrut(8));
        this.destination.setMaximumSize(new Dimension(520, 48)); this.destination.setCaretPosition(0); this.body.add(this.destination); this.body.add(Box.createVerticalStrut(8));
        final JButton browse = button("Choose folder", false); browse.addActionListener(e -> this.chooseFolder()); this.body.add(browse); this.body.add(Box.createVerticalStrut(20));
        this.body.add(copy("Included: our Severed Chains build, Skurfa HD backgrounds, and the Definitive menu refinements.", 16, MUTED)); this.body.add(Box.createVerticalStrut(24));
        this.primary("Install Definitive   →", () -> {
          try { this.root = Path.of(this.destination.getText()); }
          catch(final RuntimeException e) { this.message("Choose a valid installation folder."); return; }
          this.run("Installing your edition…", () -> PortableSetup.install(this.packageRoot, new InstallStore(this.root)), () -> { this.step = 1; this.render(); });
        });
      }
      case 1 -> {
        this.heading("Bring your adventure.", "Choose your four US disc images, or the archives containing them. We’ll find the discs, unpack them, and put everything in place.");
        this.body.add(infoCard("YOUR FILES. TAKEN CARE OF.", "BIN / raw ISO · ZIP · RAR · 7z", "Choose several files at once. Your originals stay untouched; unrelated files are left out."));
        this.body.add(Box.createVerticalStrut(28));
        this.primary("Choose disc files   →", () -> this.chooseDiscs());
        this.body.add(Box.createVerticalStrut(12)); this.body.add(copy("Already imported your discs? We’ll verify them before continuing.", 14, MUTED));
        final JButton existing = button("Use installed discs", false); existing.addActionListener(e -> this.run("Verifying your discs…", () -> { return new InstallStore(this.root).prepareDiscs(); }, () -> { this.step = 2; this.render(); })); this.body.add(existing);
      }
      default -> {
        this.heading("At home on your Deck.", "Add Definitive to your Steam library and launch it from Gaming Mode, alongside the rest of your games.");
        this.body.add(infoCard("ONE LIBRARY. ONE LAUNCH.", "Ready for Steam", "Close Steam first. We’ll add a shortcut and retain a backup of your library settings."));
        this.body.add(Box.createVerticalStrut(28));
        this.primary("Add to Steam   →", () -> this.addSteam(true));
        this.body.add(Box.createVerticalStrut(12)); final JButton skip = button("Finish without adding to Steam", false); skip.addActionListener(e -> this.finish()); this.body.add(skip);
      }
    }
  }
  private void launcher() {
    this.heading("Return to the legend.", "Your adventure is ready when you are.");
    this.body.add(infoCard("YOUR EDITION", "Definitive, thoughtfully refined.", "HD artwork and menu refinements. Gameplay presets are chosen when starting a new campaign."));
    this.body.add(Box.createVerticalStrut(30));
    this.primary("▶   Play", () -> this.run("Enjoy your adventure. Close the game to return here.", () -> { final int code = new InstallStore(this.root).play(); return code == 0 ? "Welcome back. Your progress is saved in your installation." : "Game closed with code " + code + ". Your files are retained; see the workspace log."; }, () -> { }));
    this.body.add(Box.createVerticalStrut(16));
    final JPanel secondary = new JPanel(new GridLayout(1, 2, 12, 0)); secondary.setOpaque(false); secondary.setAlignmentX(LEFT_ALIGNMENT); secondary.setMaximumSize(new Dimension(520, 48));
    final JButton mods = button("Mods & artwork", false); mods.addActionListener(e -> this.mods());
    final JButton restore = button("Restore version", false); restore.addActionListener(e -> {
      if(ManagerDialogs.restore(this.frame)) this.run("Restoring your previous version…", () -> new InstallStore(this.root).rollback(), () -> { this.candidate = null; this.render(); });
    });
    secondary.add(mods); secondary.add(restore); this.body.add(secondary);
    this.body.add(Box.createVerticalStrut(20));
    final JButton steam = button("Add to Steam library", false); steam.addActionListener(e -> this.addSteam(false)); this.body.add(steam);
    if(this.candidate != null) { this.body.add(Box.createVerticalStrut(12)); final JButton update = button("Install update · " + this.candidate.tag(), false); update.addActionListener(e -> this.run("Downloading and verifying your update…", () -> ReleaseUpdates.install(new InstallStore(this.root), this.candidate), () -> { this.candidate = null; this.render(); this.updates.setText("Update installed · Previous version retained"); })); this.body.add(update); }
  }
  private void finish() { this.launcher = true; this.render(); this.message("You’re ready. Your edition is installed and your discs are prepared."); if(this.frame != null) this.checkUpdates(); }
  boolean isBusy() { return this.busy; }
  private void chooseFolder() {
    final List<Path> paths = TouchFilePicker.choose(this.frame, this.root.getParent(), true);
    if(!paths.isEmpty()) {
      final Path chosen = paths.getFirst();
      this.destination.setText((Files.isRegularFile(chosen.resolve("state.properties")) ? chosen : chosen.resolve("Legend-of-Dragoon-Definitive")).toString());
      this.destination.setCaretPosition(0);
    }
  }
  private void chooseDiscs() {
    final List<Path> paths = TouchFilePicker.choose(this.frame, Path.of(System.getProperty("user.home")), false);
    if(paths.isEmpty()) return;
    this.run("Finding, unpacking, and verifying your discs…", () -> {
      final var store = new InstallStore(this.root);
      DiscSources.importSelected(store, paths);
      return store.prepareDiscs();
    }, () -> { this.step = 2; this.render(); });
  }
  private void addSteam(final boolean finish) {
    this.run("Preparing your Steam shortcut…", () -> SteamLibrary.accounts(), accounts -> {
      if(accounts.isEmpty()) { this.message("No Steam account found. Sign in to Steam once, close it, and try again."); return; }
      final SteamLibrary.Account selected = accounts.size() == 1 ? accounts.getFirst() : ManagerDialogs.account(this.frame, accounts);
      if(selected == null) return;
      this.run("Adding Definitive to your library…", () -> SteamLibrary.add(selected, this.root), () -> { if(finish) this.finish(); });
    });
  }
  private void mods() {
    this.run("Loading your preferences…", () -> !"original".equals(new InstallStore(this.root).state().getProperty("artwork", "hd")), hd -> {
      final JCheckBox artwork = new JCheckBox("Skurfa HD backgrounds", hd); artwork.setFont(font(18, false)); artwork.setOpaque(false); artwork.setMaximumSize(new Dimension(520, 52)); artwork.setPreferredSize(new Dimension(520, 52));
      final JPanel options = column(); options.add(artwork); options.add(Box.createVerticalStrut(12)); options.add(copy("Artwork is independent of Faithful / Definitive gameplay. Add optional mod JARs in the active data folder’s mods directory.", 16, MUTED));
      if(ManagerDialogs.confirm(this.frame, "Mods & artwork", options, "Save preference")) this.run("Saving your preference…", () -> { new InstallStore(this.root).setArtwork(artwork.isSelected()); return "Artwork preference saved for your next Play."; }, () -> { });
    });
  }
  private void checkUpdates() {
    this.updates.setText("Checking for updates…");
    new SwingWorker<java.util.Optional<ReleaseUpdates.Candidate>, Void>() {
      @Override protected java.util.Optional<ReleaseUpdates.Candidate> doInBackground() throws Exception { return ReleaseUpdates.check(new InstallStore(ManagerView.this.root)); }
      @Override protected void done() {
        try { ManagerView.this.candidate = this.get().orElse(null); ManagerView.this.updates.setText(ManagerView.this.candidate == null ? "No compatible release update available · Ready to play" : "An update is available · " + ManagerView.this.candidate.tag()); if(!ManagerView.this.busy) ManagerView.this.render(); }
        catch(final Exception e) { ManagerView.this.updates.setText("Update check unavailable · Offline play is ready"); }
      }
    }.execute();
  }
  private void run(final String working, final Action<String> action, final Runnable done) { this.run(working, action, result -> { this.message(result); done.run(); }); }
  private <T> void run(final String working, final Action<T> action, final java.util.function.Consumer<T> done) {
    if(this.busy) return; this.busy = true; this.message(working); this.progress.setVisible(true); enable(this.body, false);
    new SwingWorker<T, Void>() {
      @Override protected T doInBackground() throws Exception { return action.run(); }
      @Override protected void done() {
        ManagerView.this.busy = false; ManagerView.this.progress.setVisible(false); enable(ManagerView.this.body, true);
        try { done.accept(this.get()); }
        catch(final Exception failure) { final Throwable cause = failure.getCause() != null ? failure.getCause() : failure; ManagerView.this.message(cause.getMessage() == null ? "This operation couldn’t finish. Your files have been retained." : cause.getMessage()); }
      }
    }.execute();
  }
  private void message(final String text) { this.status.setText("<html><div style='width:360px'>" + escape(text) + "</div></html>"); }
  @FunctionalInterface private interface Action<T> { T run() throws Exception; }
  private static void enable(final Container root, final boolean enabled) { for(final Component c : root.getComponents()) { c.setEnabled(enabled); if(c instanceof Container container) enable(container, enabled); } }
  private void primary(final String title, final Runnable action) { final JButton button = button(title, true); button.addActionListener(e -> action.run()); this.body.add(button); if(this.frame != null) this.frame.getRootPane().setDefaultButton(button); }
  private static JPanel column() { final JPanel p = new JPanel(); p.setOpaque(false); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); p.setAlignmentX(LEFT_ALIGNMENT); return p; }
  private static Font font(final int size, final boolean bold) { return new Font("Helvetica Neue", bold ? Font.BOLD : Font.PLAIN, size); }
  private static JLabel label(final String text, final int size, final Color colour) { final JLabel l = new JLabel(text); l.setFont(font(size, false)); l.setForeground(colour); l.setAlignmentX(LEFT_ALIGNMENT); return l; }
  private static JLabel copy(final String text, final int size, final Color colour) { return label("<html><div style='width:360px'>" + escape(text) + "</div></html>", size, colour); }
  private static String escape(final String text) { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
  private static JPanel infoCard(final String caption, final String title, final String detail) {
    final JPanel p = column(); p.setOpaque(true); p.setBackground(new Color(0xeeeee6)); p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, new Color(0xa69a71)), BorderFactory.createEmptyBorder(20, 20, 20, 16))); p.setMaximumSize(new Dimension(520, 158));
    p.add(label(caption, 12, MUTED)); p.add(Box.createVerticalStrut(10)); p.add(label(title, 21, INK)); p.add(Box.createVerticalStrut(10)); final JLabel description = label("<html><div style='width:330px'>" + escape(detail) + "</div></html>", 16, MUTED); p.add(description); return p;
  }
  static JButton button(final String text, final boolean primary) {
    final JButton b = new JButton(text) {
      @Override protected void paintComponent(final Graphics graphics) {
        final Graphics2D g = (Graphics2D)graphics.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(primary ? (this.getModel().isRollover() ? new Color(0x326450) : GREEN) : (this.getModel().isRollover() ? new Color(0xe6e8df) : new Color(0xeceee7)));
        if(!this.isEnabled()) g.setColor(new Color(0xd9ddd4));
        g.fillRoundRect(0, 0, this.getWidth(), this.getHeight(), 18, 18);
        if(this.isFocusOwner()) { g.setColor(new Color(0x8eac9c)); g.setStroke(new BasicStroke(2)); g.drawRoundRect(2, 2, this.getWidth() - 5, this.getHeight() - 5, 16, 16); }
        g.dispose(); super.paintComponent(graphics);
      }
    };
    b.setFont(font(primary ? 21 : 16, primary)); b.setForeground(primary ? new Color(0xffffff) : GREEN); b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false); b.setRolloverEnabled(true); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); b.setAlignmentX(LEFT_ALIGNMENT); b.setMaximumSize(new Dimension(520, primary ? 60 : 48)); b.setPreferredSize(new Dimension(460, primary ? 60 : 48)); b.setMinimumSize(new Dimension(100, primary ? 60 : 48)); return b;
  }
  private static final class Hero extends JPanel {
    private BufferedImage image;
    Hero() { this.setPreferredSize(new Dimension(440, 700)); try(final var in = ManagerView.class.getResourceAsStream("hero.png")) { if(in != null) this.image = ImageIO.read(in); } catch(final Exception ignored) { /* Branding stays readable when preview art is unavailable. */ } }
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
      g.setColor(new Color(0xf6efd9)); g.setFont(serif.deriveFont(44f)); g.drawString("A legend.", 40, h - 196); g.drawString("Reawakened.", 40, h - 148);
      g.setColor(new Color(0xd5d6c6)); g.setFont(font(16, false)); g.drawString("Faithful at heart. Refined for today.", 42, h - 102);
      g.setColor(new Color(0xadaf9d)); g.setFont(font(12, false)); g.drawString("HD artwork by Skurfa · Built on Severed Chains", 42, h - 36); g.dispose();
    }
  }
}
