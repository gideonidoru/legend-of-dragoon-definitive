// Definitive selected-only retained-file review, AGPL v3; see LICENSE.
package legend.definitive.manager;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** A cached inventory view. Filesystem discovery and deletion belong to InstallStore workers. */
final class StorageReviewPanel extends JPanel {
  private final InstallStore.RetentionInventory inventory;
  private final Set<String> selected = new LinkedHashSet<>();
  private final JList<InstallStore.RetainedItem> list;
  private final JButton remove = ManagerView.button("Remove selected files", false);
  private final JLabel summary = ManagerView.copy("No files selected.", 14, ManagerView.MUTED);
  StorageReviewPanel(final InstallStore.RetentionInventory inventory, final Predicate<List<String>> confirmed, final Consumer<List<String>> removeSelected) {
    super(new BorderLayout(0, 10)); this.inventory = inventory; this.setOpaque(false); this.setAlignmentX(LEFT_ALIGNMENT);
    this.setPreferredSize(new Dimension(520, 294)); this.setMaximumSize(new Dimension(520, 294));
    final JLabel status = ManagerView.copy(inventory.complete() ? "Saves, settings, custom mods, snapshots and recovery folders are protected." : "Cleanup unavailable: " + inventory.detail(), 14, ManagerView.MUTED);
    status.getAccessibleContext().setAccessibleDescription(inventory.detail()); this.add(status, BorderLayout.NORTH);
    this.list = new JList<>(inventory.items().toArray(InstallStore.RetainedItem[]::new)); this.list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); this.list.setFixedCellHeight(82); this.list.setBackground(Color.WHITE); this.list.setFont(ManagerView.font(15, false)); this.list.setCellRenderer(this::row);
    this.list.getAccessibleContext().setAccessibleName("Retained installation files"); this.list.getAccessibleContext().setAccessibleDescription("Only inactive verified items can be checked for removal; protected files cannot be selected.");
    this.list.getActionMap().put("activate", new AbstractAction() { @Override public void actionPerformed(final java.awt.event.ActionEvent event) { final var item = StorageReviewPanel.this.list.getSelectedValue(); if(item != null) StorageReviewPanel.this.toggle(item); } });
    for(final int key : new int[]{java.awt.event.KeyEvent.VK_ENTER, java.awt.event.KeyEvent.VK_SPACE}) this.list.getInputMap().put(KeyStroke.getKeyStroke(key, 0), "activate");
    this.list.addMouseListener(new java.awt.event.MouseAdapter() { @Override public void mouseReleased(final java.awt.event.MouseEvent event) { final int index = StorageReviewPanel.this.list.locationToIndex(event.getPoint()); if(index >= 0 && StorageReviewPanel.this.list.getCellBounds(index, index).contains(event.getPoint())) { StorageReviewPanel.this.list.setSelectedIndex(index); StorageReviewPanel.this.toggle(StorageReviewPanel.this.list.getModel().getElementAt(index)); } } });
    if(!inventory.items().isEmpty()) this.list.setSelectedIndex(0);
    final JScrollPane scroll = new JScrollPane(this.list); scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER); scroll.setBorder(ManagerView.roundedBorder()); scroll.getVerticalScrollBar().setPreferredSize(new Dimension(44, 0)); scroll.getVerticalScrollBar().setUnitIncrement(82);
    scroll.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
      @Override protected void configureScrollBarColors() { this.thumbColor = ManagerView.GREEN; this.trackColor = ManagerView.PAPER; }
      private JButton hiddenArrow() { final JButton button = new JButton(); button.setVisible(false); button.setPreferredSize(new Dimension(0, 0)); return button; }
      @Override protected JButton createDecreaseButton(final int orientation) { return this.hiddenArrow(); }
      @Override protected JButton createIncreaseButton(final int orientation) { return this.hiddenArrow(); }
    }); this.add(scroll, BorderLayout.CENTER);
    final JPanel actions = new JPanel(new BorderLayout(0, 8)); actions.setOpaque(false); actions.add(this.summary, BorderLayout.NORTH); actions.add(this.remove, BorderLayout.SOUTH); this.add(actions, BorderLayout.SOUTH);
    this.remove.setEnabled(false); this.remove.addActionListener(event -> { final List<String> chosen = this.selectedPaths(); if(!chosen.isEmpty() && this.inventory.complete() && confirmed.test(chosen)) removeSelected.accept(chosen); });
  }
  List<String> selectedPaths() { return List.copyOf(this.selected); }
  JButton removeAction() { return this.remove; }
  JList<InstallStore.RetainedItem> entries() { return this.list; }
  private boolean removable(final InstallStore.RetainedItem item) { return this.inventory.complete() && item.removable(); }
  private void toggle(final InstallStore.RetainedItem item) {
    if(!this.removable(item)) return;
    final boolean wasSelected = this.selected.remove(item.path()); if(!wasSelected) this.selected.add(item.path());
    long bytes = 0; for(final var candidate : this.inventory.items()) if(this.selected.contains(candidate.path())) bytes = Math.addExact(bytes, candidate.bytes());
    this.summary.setText(ManagerView.copy(this.selected.isEmpty() ? "No files selected." : this.selected.size() + " selected · " + bytes(bytes), 14, ManagerView.MUTED).getText()); this.remove.setEnabled(!this.selected.isEmpty());
    this.list.getAccessibleContext().firePropertyChange(javax.accessibility.AccessibleContext.ACCESSIBLE_STATE_PROPERTY, wasSelected ? javax.accessibility.AccessibleState.CHECKED : null, wasSelected ? null : javax.accessibility.AccessibleState.CHECKED); this.list.repaint();
  }
  private Component row(final JList<? extends InstallStore.RetainedItem> list, final InstallStore.RetainedItem item, final int index, final boolean focused, final boolean cellFocus) {
    final JPanel row = new JPanel(new BorderLayout(8, 0)); row.setOpaque(true); row.setBackground(focused ? new Color(0xdfe9df) : index % 2 == 0 ? Color.WHITE : new Color(0xf8f9f5)); row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(cellFocus ? ManagerView.GREEN : row.getBackground(), 2), BorderFactory.createEmptyBorder(7, 8, 7, 8)));
    final JCheckBox check = new JCheckBox(); check.setOpaque(false); check.setFocusable(false); check.setSelected(this.selected.contains(item.path())); check.setEnabled(this.removable(item)); check.setPreferredSize(new Dimension(28, 28)); check.getAccessibleContext().setAccessibleName(item.path()); check.getAccessibleContext().setAccessibleDescription(bytes(item.bytes()) + " · " + item.reason()); check.addActionListener(event -> this.toggle(item)); row.add(check, BorderLayout.WEST);
    final JPanel details = new JPanel(new BorderLayout(0, 4)); details.setOpaque(false);
    final JLabel path = new JLabel(item.path()); path.setFont(ManagerView.font(15, true)); path.setForeground(ManagerView.INK); path.setToolTipText(item.path()); details.add(path, BorderLayout.NORTH);
    final JLabel reason = ManagerView.copy((this.removable(item) ? "Can remove · " : "Protected · ") + bytes(item.bytes()) + "\n" + shortReason(item.reason()), 12, ManagerView.MUTED); reason.setToolTipText(item.reason()); reason.getAccessibleContext().setAccessibleDescription(item.reason()); details.add(reason, BorderLayout.CENTER); row.add(details, BorderLayout.CENTER);
    row.getAccessibleContext().setAccessibleName(item.path()); row.getAccessibleContext().setAccessibleDescription(bytes(item.bytes()) + " · " + item.reason());
    // Renderer panels are detached; lay out their nested children before painting or accessibility inspection.
    row.setSize(Math.max(1, list.getWidth() > 0 ? list.getWidth() : 520), list.getFixedCellHeight()); row.doLayout(); details.doLayout();
    return row;
  }
  private static String shortReason(final String reason) {
    final FontMetrics metrics = new JLabel().getFontMetrics(ManagerView.font(12, false));
    if(metrics.stringWidth(reason) <= 360) return reason;
    int end = reason.length(); while(end > 0 && metrics.stringWidth(reason.substring(0, end) + "…") > 360) end = reason.offsetByCodePoints(end, -1);
    return reason.substring(0, end) + "…";
  }
  static String bytes(final long count) { if(count < 1024) return count + " B"; final String[] units = {"KiB", "MiB", "GiB", "TiB"}; double amount = count; int unit = -1; do { amount /= 1024; unit++; } while(amount >= 1024 && unit < units.length - 1); return String.format(java.util.Locale.ROOT, "%.1f %s", amount, units[unit]); }
}
