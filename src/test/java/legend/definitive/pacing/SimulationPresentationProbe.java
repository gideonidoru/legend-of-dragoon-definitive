package legend.definitive.pacing;

import legend.core.Config;
import legend.core.GameEngine;
import legend.core.lang.RawText;
import legend.core.platform.NoopPlatformManager;
import legend.core.platform.NoopWindow;
import legend.core.platform.Window;
import legend.core.renderer.*;
import legend.game.modding.coremod.CoreMod;
import legend.game.saves.ConfigRegistryEvent;
import legend.game.textures.Image;
import legend.game.textures.NativeUiTextures;
import legend.game.ui.GameOverlay;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** Complete production onDraw replay in a separate headless JVM; no native window/context. */
public final class SimulationPresentationProbe {
  private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
  private static Object field(Object owner,String name) throws Exception {
    final var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
  }
  private static void field(Object owner,String name,Object value) throws Exception {
    final var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);
  }
  private static final class DrawObj extends Obj {
    boolean destroyed;int draws;
    DrawObj() { super("Retained simulation frame"); }
    @Override protected void performDelete() { this.destroyed=true; }
    @Override public boolean hasTexture() { return false; }
    @Override public boolean hasTexture(int i) { return false; }
    @Override public boolean hasTranslucency() { return false; }
    @Override public boolean hasTranslucency(int i) { return false; }
    @Override public boolean shouldRender(Translucency t) { return t==null; }
    @Override public boolean shouldRender(Translucency t,int layer) { return t==null; }
    @Override public int getLayers() { return 1; }
    @Override public void render(int layer,int start,int count) { require(!this.destroyed,"Retained frame drew a destroyed object");this.draws++; }
    @Override public void render(Translucency t,int layer,int start,int count) { this.render(layer,start,count); }
  }

  public static void main(String[] args) throws Exception {
    CoreMod.registerConfig(new ConfigRegistryEvent((legend.game.saves.ConfigRegistry)GameEngine.REGISTRIES.config));
    final var access=GameEngine.class.getDeclaredField("EVENT_ACCESS");access.setAccessible(true);
    ((org.legendofdragoon.modloader.events.EventManager.Access)access.get(null)).initialize(GameEngine.MODS);
    final var renderer=GameEngine.RENDERER;
    final var clock=(SimulationClock)field(renderer,"simulationClock");final var now=new AtomicLong();
    field(clock,"clock",(LongSupplier)now::get);clock.reset(false);
    final var window=new NoopWindow(new NoopPlatformManager(),640,480);
    final var init=RenderEngine.class.getDeclaredMethod("init",Window.class);init.setAccessible(true);init.invoke(renderer,window);
    GameEngine.GPU.init();GameEngine.DEFAULT_FONT.init();
    final var resize=RenderEngine.class.getDeclaredMethod("onResize",Window.class,int.class,int.class);
    resize.setAccessible(true);resize.invoke(renderer,window,640,480);
    final var tick=NoopWindow.class.getDeclaredMethod("tick");tick.setAccessible(true);
    final var batch=(RenderBatch)field(renderer,"mainBatch");
    final var current=new DrawObj[1];final int[] steps={0};
    final var scissored=new QueuedModelStandard[1];
    renderer.setSimulationCallback(()->{
      steps[0]++;if(current[0]!=null)current[0].delete();current[0]=new DrawObj();
      renderer.scissorStack.push().set(20,30,60,70);
      scissored[0]=renderer.queueUiOrthoModel(current[0],QueuedModelStandard.class);
      renderer.scissorStack.pop();
      renderer.addClutAnimation(0,0,steps[0],0);
      GameEngine.GPU.startFrame();GameEngine.GPU.endFrame();
    });
    GameOverlay.addNotification(3600,new RawText("Retained UI"));
    tick.invoke(window);require(steps[0]==1,"First simulation presentation advances exactly once");
    final int queued=batch.orthoPool.size(),history=(int)field(renderer,"renderBufferIndex");
    final var obj=current[0];final int previousDraws=obj.draws;
    for(int i=0;i<10;i++){now.addAndGet(100_000);tick.invoke(window);}
    require(steps[0]==1&&obj.draws==previousDraws+10&&!obj.destroyed,"Zero-step production onDraw redraws retained objects without advancing/deleting them");
    require(batch.orthoPool.size()==queued,"Zero-step notifications do not accumulate queued glyphs");
    require(history==(int)field(renderer,"renderBufferIndex"),"Zero-step framebuffer history stays stable");
    final var retained=scissored[0].worldScissor();
    require(retained.x==20&&retained.y==30&&retained.w==60&&retained.h==70,"Retained scissor remains a value snapshot");
    now.addAndGet(99_000_000);tick.invoke(window);
    require(steps[0]==7&&obj.destroyed,"Catch-up retires discarded objects and advances all native GPU ticks");
    require(batch.orthoPool.size()==queued,"Catch-up keeps only final scene/notification queues");

    // Actual preparation/touch path runs again after beginFrame on a zero-step presentation.
    final byte[] nativePixels=new byte[16*16*4],enhancedPixels=new byte[64*64*4];
    for(int i=0;i<nativePixels.length;i+=4)nativePixels[i]=100;
    for(int i=0;i<enhancedPixels.length;i+=4)enhancedPixels[i]=120;
    require(NativeUiTextures.register(new NativeUiTextures.Binding(0,0,32,496,16,16),new Image(nativePixels,16,16),new Image(enhancedPixels,64,64)),"Native UI source registered");
    final var hud=new QuadBuilder("Retained native HUD").bpp(legend.core.gpu.Bpp.BITS_4).size(16,16).uvSize(16,16).clut(32,496).build();
    renderer.setSimulationCallback(()->renderer.queueUiOrthoModel(hud,QueuedModelStandard.class));
    tick.invoke(window);require(NativeUiTextures.residentCount()==1,"Production HUD preparation uploads once");
    final long bytes=NativeUiTextures.allocatedBytes();
    for(int i=0;i<10;i++){now.addAndGet(100_000);tick.invoke(window);require(NativeUiTextures.residentCount()==1&&NativeUiTextures.allocatedBytes()==bytes,"Zero-step UI frame pins preserve resident ownership");}
    renderer.addTask(NativeUiTextures::clear);tick.invoke(window);
    require(NativeUiTextures.allocatedBytes()==0,"Zero-step UI disable falls back without retaining retired allocations");

    final int[] game={0},movie={0};
    renderer.setSimulationCallback(()->game[0]++);tick.invoke(window);
    renderer.addTask(()->{renderer.setRenderCallback(()->movie[0]++);now.addAndGet(200_000_000);});
    tick.invoke(window);require(game[0]==1&&movie[0]==1,"Queued task switches to movie once, never through old gameplay catch-up");
    require(!window.simulationConsumesInput(),"Movie presentation retains window-owned input consumption");
    renderer.addTask(()->{renderer.setSimulationCallback(()->game[0]++);now.addAndGet(100_000_000);});
    tick.invoke(window);require(movie[0]==1&&game[0]==8,"Inverse queued handoff uses the new simulation owner and its own elapsed debt");
    require(window.simulationConsumesInput(),"Simulation handoff consumes input per tick");
    // All state/speed/presentation combinations use the production draw callback, not just the clock helper.
    final int[] rate={60},ticks={0};
    renderer.setSimulationCallback(()->{renderer.setSimulationRate(rate[0]);ticks[0]++;});
    for(int base:new int[]{20,30,60})for(int speed=1;speed<=16;speed++)for(int presentation:new int[]{15,30,40,60,120,1000}) {
      Config.setGameSpeedMultiplier(speed);rate[0]=base*speed;renderer.setSimulationRate(rate[0]);
      now.set(0);clock.reset(false);ticks[0]=0;
      for(int frame=1;frame<=presentation;frame++){now.set(frame*1_000_000_000L/presentation);tick.invoke(window);}
      require(ticks[0]==rate[0],"Production onDraw tick budget "+rate[0]+" at "+presentation+" Hz");
    }
    System.out.println("PASS: complete headless production onDraw preserves simulation budgets, retained draw/scissor/notification/UI ownership and queued movie/gameplay handoffs.");
  }
}
