package legend.core.renderer;

/** Immutable source coordinates retained for optional indexed interface artwork. */
public record NativeUiQuad(int pageX, int pageY, int clutX, int clutY, float u, float v, float width, float height) {
  public NativeUiQuad overridden(final QueuedModelStandard model) {
    if(model.tpageOverride.x == 0 && model.clutOverride.x == 0 && model.uvOffset.x == 0 && model.uvOffset.y == 0) return this;
    return new NativeUiQuad(model.tpageOverride.x == 0 ? this.pageX : (int)model.tpageOverride.x,
      model.tpageOverride.x == 0 ? this.pageY : (int)model.tpageOverride.y,
      model.clutOverride.x == 0 ? this.clutX : (int)model.clutOverride.x,
      model.clutOverride.x == 0 ? this.clutY : (int)model.clutOverride.y,
      this.u + model.uvOffset.x, this.v + model.uvOffset.y, this.width, this.height);
  }
}
