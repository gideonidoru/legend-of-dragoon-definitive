// Definitive touch-ready dialogs (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

final class ManagerDialogs {
  private ManagerDialogs() { }
  static boolean confirm(final JFrame owner, final String title, final JComponent content, final String action) {
    final boolean[] accepted = {false};
    final JDialog dialog = new JDialog(owner, title, true); final JPanel panel = new JPanel(new BorderLayout(0, 24)); panel.setBackground(ManagerView.PAPER); panel.setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28)); panel.add(content, BorderLayout.CENTER);
    final JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 0)); buttons.setOpaque(false);
    final JButton cancel = ManagerView.button("Cancel", false), accept = ManagerView.button(action, true);
    cancel.addActionListener(e -> dialog.dispose()); accept.addActionListener(e -> { accepted[0] = true; dialog.dispose(); }); buttons.add(cancel); buttons.add(accept); panel.add(buttons, BorderLayout.SOUTH);
    dialog.setContentPane(panel); dialog.getRootPane().setDefaultButton(accept);
    dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
    dialog.getRootPane().getActionMap().put("cancel", new AbstractAction() { @Override public void actionPerformed(final ActionEvent e) { dialog.dispose(); } });
    dialog.setSize(660, 340); dialog.setLocationRelativeTo(owner); dialog.addWindowListener(new WindowAdapter() { @Override public void windowOpened(final WindowEvent e) { accept.requestFocusInWindow(); } }); dialog.setVisible(true); return accepted[0];
  }
  static boolean restore(final JFrame owner) {
    final JLabel content = new JLabel("<html><div style='width:400px'>Restore the previous version and its pre-update saves and settings?<br><br>Your newer data will be retained separately.</div></html>"); content.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18)); content.setForeground(ManagerView.INK);
    return confirm(owner, "Restore previous version", content, "Restore version");
  }
  static SteamLibrary.Account account(final JFrame owner, final List<SteamLibrary.Account> accounts) {
    final JList<SteamLibrary.Account> list = new JList<>(accounts.toArray(SteamLibrary.Account[]::new)); list.setFixedCellHeight(52); list.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18)); list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); list.setSelectedIndex(0);
    list.getActionMap().put("activate", new AbstractAction() { @Override public void actionPerformed(final ActionEvent e) { list.transferFocus(); } });
    return confirm(owner, "Choose your Steam account", new JScrollPane(list), "Use account") ? list.getSelectedValue() : null;
  }
}
