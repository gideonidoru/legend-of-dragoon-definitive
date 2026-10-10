package legend.definitive.materials;

import legend.game.types.Model124;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CharacterMaterialStateTest {
  @Test void pairsBothArrivalOrdersAndConsumesEachChangeOnce() {
    for(final boolean textureFirst : new boolean[]{false, true}) {
      final var state = new CharacterMaterialState();
      final var model = new Model124("fixture");
      final byte[] geometry = {1}, texture = {2};
      if(textureFirst) { state.textureReady(texture); assertNull(state.takeUpdate(model)); }
      state.modelReady(model, geometry);
      if(!textureFirst) {
        assertFalse(state.takeUpdate(model).ready());
        assertNull(state.takeUpdate(model));
        state.textureReady(texture);
      }
      final var update = state.takeUpdate(model);
      assertTrue(update.ready());
      assertSame(geometry, update.modelSource()); assertSame(texture, update.timSource());
      assertNull(state.takeUpdate(model));
      state.textureReady(null);
      assertFalse(state.takeUpdate(model).ready());
    }
  }

  @Test void teardownRejectsDelayedSourcesAndOldModelOwners() {
    final var state = new CharacterMaterialState();
    final var oldModel = new Model124("old");
    final var replacement = new Model124("new");
    final int previous = state.generation();
    state.modelReady(oldModel, new byte[]{1}); state.textureReady(new byte[]{2});
    state.invalidate();
    state.runIfCurrent(previous, () -> fail("Stale callback executed"));
    assertNull(state.takeUpdate(oldModel));
    state.runIfCurrent(state.generation(), () -> state.textureReady(new byte[]{3}));
    state.modelReady(replacement, new byte[]{4});
    assertNull(state.takeUpdate(oldModel));
    assertTrue(state.takeUpdate(replacement).ready());
  }
}
