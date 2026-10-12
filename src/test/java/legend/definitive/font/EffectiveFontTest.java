package legend.definitive.font;

import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import legend.core.font.EffectiveFont;
import legend.core.font.Font;
import legend.core.font.Glyph;
import legend.core.lang.RawText;
import legend.core.renderer.RenderEngine;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class EffectiveFontTest {
  private static final class TrackedFont extends Font {
    int initialized, released;
    boolean fail;
    TrackedFont(int width) { super(Path.of("fixture.json"), new RawText("fixture"), glyphs(width)); }
    public void init() { initialized++; if(fail) throw new IllegalStateException("upload failed"); }
    public void releaseOwnedResources() { released++; }
    protected boolean hasOwnedResources() { return initialized > 0; }
  }
  private static Char2ObjectOpenHashMap<Glyph> glyphs(int width) {
    var glyphs = new Char2ObjectOpenHashMap<Glyph>();
    glyphs.put('A',new Glyph('A',0,0,1,1,0,0,width,16,true)); return glyphs;
  }
  @Test void capturedReferenceFollowsEnableDisableAndConfigurationWithoutOwningConfiguredFonts() {
    var pending = new ArrayList<Runnable>(); var original = new TrackedFont(8); var configured = new TrackedFont(6);
    var hd = new TrackedFont(8); var effective = new EffectiveFont(original,pending::add); Font captured = effective;
    effective.configure(configured); long generation = effective.generation();
    effective.selectOwned(hd); assertSame(configured,effective.configured()); assertEquals(8,captured.charWidth('A'));
    assertTrue(effective.generation()>generation); effective.init();
    effective.selectOwned(null); assertEquals(6,captured.charWidth('A')); assertEquals(0,hd.released);
    pending.removeFirst().run(); assertEquals(1,hd.released); assertEquals(0,configured.released); assertEquals(0,original.released);
    var again = new TrackedFont(8); effective.selectOwned(again); effective.init(); assertEquals(8,captured.charWidth('A'));
    effective.configure(original); assertEquals(8,captured.charWidth('A')); effective.selectOwned(null); assertSame(original,effective.configured());
  }
  @Test void failedUploadRestoresConfiguredFontInSameInitAndCanReenable() {
    var pending = new ArrayList<Runnable>(); var configured = new TrackedFont(6); var hd = new TrackedFont(8); hd.fail=true;
    var effective = new EffectiveFont(configured,pending::add); effective.selectOwned(hd); long generation=effective.generation();
    effective.init(); assertEquals(6,effective.charWidth('A')); assertEquals(1,configured.initialized); assertTrue(effective.generation()>generation);
    effective.init(); assertEquals(1,hd.initialized); pending.removeFirst().run(); assertEquals(1,hd.released);
    var good = new TrackedFont(8); effective.selectOwned(good); effective.init(); assertEquals(8,effective.charWidth('A'));
  }
  @Test void duplicateSelectionDoesNotRetireLiveResourceAndSharedFontCannotBeOwned() {
    var pending=new ArrayList<Runnable>(); var configured=new TrackedFont(6); var hd=new TrackedFont(8);
    var effective=new EffectiveFont(configured,pending::add); effective.selectOwned(hd); long generation=effective.generation();
    effective.selectOwned(hd); assertEquals(generation,effective.generation()); assertTrue(pending.isEmpty());
    assertThrows(IllegalArgumentException.class,()->effective.selectOwned(configured));
    assertThrows(IllegalArgumentException.class,()->effective.configure(hd));
    effective.selectOwned(null);
    assertThrows(IllegalArgumentException.class,()->effective.selectOwned(hd));
    var other = new EffectiveFont(new TrackedFont(5),pending::add);
    assertThrows(IllegalArgumentException.class,()->other.selectOwned(hd));
  }
  @Test void rendererRetirementWaitsForRetainedBatchResetAndDrainsOnce() throws Exception {
    var renderer=new RenderEngine(); var hd=new TrackedFont(8); var effective=new EffectiveFont(new TrackedFont(6),renderer::retireAfterBatchReset);
    effective.selectOwned(hd); effective.init(); effective.selectOwned(null); assertEquals(0,hd.released);
    var reset=RenderEngine.class.getDeclaredMethod("resetBatches"); reset.setAccessible(true); reset.invoke(renderer);
    assertEquals(1,hd.released); reset.invoke(renderer); assertEquals(1,hd.released);
  }
  @Test void previouslyCompiledFontFieldDescriptorRemainsLinkable() throws Exception {
    var descriptor=java.lang.constant.ClassDesc.of("legend.definitive.font.LegacyFontConsumer");
    byte[] bytes=java.lang.classfile.ClassFile.of().build(descriptor,builder->builder.withMethodBody("font",java.lang.constant.MethodTypeDesc.of(java.lang.constant.ClassDesc.of("legend.core.font.Font")),java.lang.classfile.ClassFile.ACC_PUBLIC|java.lang.classfile.ClassFile.ACC_STATIC,code->code
      .getstatic(java.lang.constant.ClassDesc.of("legend.core.GameEngine"),"DEFAULT_FONT",java.lang.constant.ClassDesc.of("legend.core.font.Font")).areturn()));
    class Loader extends ClassLoader { Class<?> loadConsumer() { return defineClass(null,bytes,0,bytes.length); } }
    assertSame(legend.core.GameEngine.DEFAULT_FONT,new Loader().loadConsumer().getMethod("font").invoke(null));
    assertFalse(java.lang.reflect.Modifier.isFinal(legend.core.GameEngine.class.getField("DEFAULT_FONT").getModifiers()),"Legacy PUTSTATIC retains its field access contract");
    byte[] setterBytes=java.lang.classfile.ClassFile.of().build(java.lang.constant.ClassDesc.of("legend.definitive.font.LegacyFontSetter"),builder->builder.withMethodBody("set",java.lang.constant.MethodTypeDesc.of(java.lang.constant.ConstantDescs.CD_void,java.lang.constant.ClassDesc.of("legend.core.font.Font")),java.lang.classfile.ClassFile.ACC_PUBLIC|java.lang.classfile.ClassFile.ACC_STATIC,code->code
      .aload(0).putstatic(java.lang.constant.ClassDesc.of("legend.core.GameEngine"),"DEFAULT_FONT",java.lang.constant.ClassDesc.of("legend.core.font.Font")).return_()));
    class SetterLoader extends ClassLoader { Class<?> loadConsumer() { return defineClass(null,setterBytes,0,setterBytes.length); } }
    final Font previous=legend.core.GameEngine.DEFAULT_FONT;
    final Font custom=new TrackedFont(5);
    try {
      new SetterLoader().loadConsumer().getMethod("set",Font.class).invoke(null,custom);
      assertSame(custom,legend.core.GameEngine.DEFAULT_FONT,"Previously compiled PUTSTATIC remains linkable");
    } finally { legend.core.GameEngine.DEFAULT_FONT=previous; }
  }

  @Test void existingLabelRefreshesAutomaticBoundsAndPreservesExplicitBounds() {
    var pending=new ArrayList<Runnable>(); var configured=new TrackedFont(6); var effective=new EffectiveFont(configured,pending::add);
    var label=new legend.game.inventory.screens.controls.Label(new RawText("AA")); label.setFont(effective);
    assertEquals(12,label.getWidth()); effective.selectOwned(new TrackedFont(8)); assertEquals(16,label.getWidth());
    effective.selectOwned(null); assertEquals(12,label.getWidth()); label.setSize(100,30);
    effective.selectOwned(new TrackedFont(8)); assertEquals(100,label.getWidth()); assertEquals(30,label.getHeight());
    assertSame(effective,label.getFont());
  }
  @Test void configuredFontResourcesCannotBeReleasedThroughOptionalOwnershipApi() {
    var shared=new Font(Path.of("fixture.json"),new RawText("shared"),glyphs(8));
    assertThrows(IllegalStateException.class,shared::releaseOwnedResources);
  }

  @Test void liveOwnedFontCannotRetireAndRetiredOwnedFontCannotReinitialize() {
    var pending=new ArrayList<Runnable>(); var shared=new Font(Path.of("fixture.json"),new RawText("shared"),glyphs(8));
    var effective=new EffectiveFont(new TrackedFont(6),pending::add); effective.selectOwned(shared);
    assertThrows(IllegalStateException.class,shared::releaseOwnedResources); effective.selectOwned(null);
    assertThrows(IllegalStateException.class,shared::init); assertTrue(pending.isEmpty());
  }

  @Test void repeatedUninitializedSelectionsDoNotRetainCpuAtlasesInRendererQueue() {
    var pending=new ArrayList<Runnable>(); var effective=new EffectiveFont(new TrackedFont(6),pending::add);
    for(int i=0;i<100;i++) { var font=new TrackedFont(8); effective.selectOwned(font); effective.selectOwned(null); assertEquals(1,font.released); }
    assertTrue(pending.isEmpty());
  }

}
