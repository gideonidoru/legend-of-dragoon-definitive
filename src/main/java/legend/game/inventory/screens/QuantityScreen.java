package legend.game.inventory.screens;

import legend.core.lang.I18nText;
import legend.core.lang.TextComponent;
import legend.core.platform.input.InputAction;
import legend.game.inventory.screens.controls.Button;
import legend.game.inventory.screens.controls.Label;
import legend.game.inventory.screens.controls.NumberSpinner;
import legend.game.inventory.screens.controls.Panel;

import java.util.function.IntConsumer;

import static legend.game.modding.coremod.CoreMod.INPUT_ACTION_MENU_BACK;
import static legend.game.modding.coremod.CoreMod.INPUT_ACTION_MENU_CONFIRM;
import static legend.game.sound.Audio.playMenuSound;

/** Modal quantity confirmation. No inventory changes until Confirm; Back returns zero. */
public class QuantityScreen extends MenuScreen {
  private final NumberSpinner<Integer> quantity;
  private final IntConsumer onResult;
  private boolean completed;

  public QuantityScreen(final TextComponent title, final int maximum, final int unitPrice, final IntConsumer onResult) {
    if(maximum < 1 || maximum > 99 || unitPrice < 0) throw new IllegalArgumentException("Invalid shop quantity bounds");
    this.onResult = onResult;
    final Panel panel = this.addControl(Panel.panel());
    panel.setPos(54, 62);
    panel.setSize(260, 116);
    panel.setZ(32);
    final Label label = panel.addControl(new Label(title));
    label.setPos(12, 12);
    label.setSize(236, 16);
    label.setScale(0.8f);
    label.setZ(31);
    this.quantity = panel.addControl(NumberSpinner.intSpinner(1, 1, maximum));
    this.quantity.setPos(96, 35);
    this.quantity.setSize(68, 16);
    this.quantity.setZ(31);
    final Label total = panel.addControl(new Label(new I18nText("lod_core.ui.quantity.total", (long)unitPrice)));
    total.setPos(12, 58);
    total.setSize(236, 14);
    total.setZ(31);
    total.getFontOptions().horizontalAlign(HorizontalAlign.CENTRE);
    this.quantity.onChange(value -> total.setText(new I18nText("lod_core.ui.quantity.total", (long)unitPrice * value)));
    final Button accept = panel.addControl(new Button(new I18nText("lod_core.ui.input_box.accept")));
    accept.setPos(30, 83);
    accept.setSize(90, 14);
    accept.setZ(31);
    accept.onPressed(() -> this.finish(this.quantity.getNumber()));
    final Button cancel = panel.addControl(new Button(new I18nText("lod_core.ui.input_box.cancel")));
    cancel.setPos(140, 83);
    cancel.setSize(90, 14);
    cancel.setZ(31);
    cancel.onPressed(() -> this.finish(0));
    this.quantity.showHighlight();
    this.setFocus(this.quantity);
    this.addActionHint(new I18nText("lod_core.ui.quantity.change"), legend.game.modding.coremod.CoreMod.INPUT_ACTION_MENU_LEFT);
    this.addActionHint(new I18nText("lod_core.ui.input_box.accept"), INPUT_ACTION_MENU_CONFIRM);
    this.addActionHint(new I18nText("lod_core.ui.input_box.cancel"), INPUT_ACTION_MENU_BACK);
  }

  private void finish(final int quantity) {
    if(this.completed) return;
    this.completed = true;
    playMenuSound(quantity == 0 ? 3 : 2);
    this.getStack().popScreen();
    this.onResult.accept(quantity);
  }

  @Override protected void render() { }
  @Override protected boolean propagateRender() { return true; }

  @Override protected InputPropagation inputActionPressed(final InputAction action, final boolean repeat) {
    // Handle before the focused spinner, whose normal Confirm toggles focus.
    if(action == INPUT_ACTION_MENU_BACK.get()) {
      if(!repeat) this.finish(0);
      return InputPropagation.HANDLED;
    }
    if(action == INPUT_ACTION_MENU_CONFIRM.get()) {
      if(!repeat) this.finish(this.quantity.getNumber());
      return InputPropagation.HANDLED;
    }
    return super.inputActionPressed(action, repeat);
  }
}
