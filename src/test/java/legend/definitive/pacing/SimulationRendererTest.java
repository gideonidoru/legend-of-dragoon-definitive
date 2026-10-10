package legend.definitive.pacing;

import legend.core.GameEngine;
import legend.core.platform.NoopPlatformManager;
import legend.core.platform.NoopWindow;
import legend.core.platform.SdlPlatformManager;
import legend.core.platform.input.InputAction;
import legend.core.platform.input.InputActionState;
import legend.core.renderer.*;
import legend.core.renderer.noop.NoopApi;
import org.junit.jupiter.api.Test;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the production renderer's simulation/lifetime paths without a window or graphics context. */
final class SimulationRendererTest {
  private static Object get(final Object target, final String name) throws Exception {
    final var f=target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
  }
  private static void set(final Object target, final String name, final Object value) throws Exception {
    final var f=target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target,value);
  }

  private static final class TickObj extends Obj {
    boolean destroyed;
    TickObj() { super("Simulation tick lifetime fixture"); }
    @Override protected void performDelete() { this.destroyed=true; }
    @Override public boolean hasTexture() { return false; }
    @Override public boolean hasTexture(int index) { return false; }
    @Override public boolean hasTranslucency() { return false; }
    @Override public boolean hasTranslucency(int index) { return false; }
    @Override public boolean shouldRender(Translucency t) { return !this.destroyed; }
    @Override public boolean shouldRender(Translucency t,int layer) { return !this.destroyed; }
    @Override public int getLayers() { return 1; }
    @Override public void render(int layer,int first,int count) { assertFalse(this.destroyed); }
    @Override public void render(Translucency t,int layer,int first,int count) { assertFalse(this.destroyed); }
  }

  private static final class Fixture implements AutoCloseable {
    final AtomicLong now=new AtomicLong();
    final SimulationClock clock=new SimulationClock(this.now::get);
    final RenderEngine renderer;
    final List<float[]> cluts=new ArrayList<>();
    Fixture() throws Exception {
      assertNotNull(GameEngine.GTE); // Initialize global CPU owners before fixture resources.
      final var constructor=RenderEngine.class.getDeclaredConstructor(SimulationClock.class);
      constructor.setAccessible(true); this.renderer=constructor.newInstance(this.clock);
      final var api=new NoopApi();
      set(this.renderer,"api",api);
      set(this.renderer,"window",new NoopWindow(new NoopPlatformManager(),1280,800));
      set(this.renderer,"camera2d",new BasicCamera(0,0));
      set(this.renderer,"camera3d",new QuaternionCamera(0,0,0));
      set(this.renderer,"projectionUniform",api.makeUniformBuffer(16,3));
      set(this.renderer,"transformsUniform",api.makeUniformBuffer(128,0));
      set(this.renderer,"clutAnimationUniform",new ShaderUniformBuffer() {
        @Override public void delete() { }
        @Override public void set(FloatBuffer data) {
          final float[] copy=new float[5]; for(int i=0;i<5;i++)copy[i]=data.get(i); cluts.add(copy);
        }
        @Override public void set(long offset,FloatBuffer data) { this.set(data); }
      });
    }
    int present() throws Exception {
      final var m=RenderEngine.class.getDeclaredMethod("tickSimulation"); m.setAccessible(true);
      return (int)m.invoke(this.renderer);
    }
    RenderBatch batch() throws Exception { return (RenderBatch)get(this.renderer,"mainBatch"); }
    @Override public void close() throws Exception {
      final var m=RenderEngine.class.getDeclaredMethod("releaseSimulationFrame");m.setAccessible(true);m.invoke(this.renderer);
    }
  }

  @Test void zeroStepPresentationRetainsObjectsAndCatchUpKeepsOnlyTheFinalQueueAndClut() throws Exception {
    try(final var f=new Fixture()) {
      final List<TickObj> objects=new ArrayList<>(); final int[] ticks={0};
      f.renderer.setSimulationCallback(()->{
        ticks[0]++; final var obj=new TickObj(); objects.add(obj);
        f.renderer.queueModel(obj,QueuedModelStandard.class).ui().monochrome(ticks[0]);
        f.renderer.addClutAnimation(0,0,ticks[0],0); obj.delete();
      });
      assertEquals(1,f.present()); assertEquals(1,f.batch().modelPool.size());
      final var retained=f.batch().modelPool.get(0); final int buffer=(int)get(f.renderer,"renderBufferIndex");
      f.now.addAndGet(1_000_000); assertEquals(0,f.present());
      assertSame(retained,f.batch().modelPool.get(0)); assertFalse(objects.get(0).destroyed);
      assertEquals(buffer,get(f.renderer,"renderBufferIndex")); assertEquals(1,f.cluts.size());
      f.now.addAndGet(99_000_000); assertEquals(6,f.present());
      assertEquals(7,ticks[0]); assertEquals(1,f.batch().modelPool.size());
      for(int i=0;i<objects.size()-1;i++)assertTrue(objects.get(i).destroyed);
      assertFalse(objects.getLast().destroyed);
      assertEquals((buffer+1)%2,get(f.renderer,"renderBufferIndex"),"Image history advances once, not once per discarded tick");
      for(int i=0;i<f.cluts.size();i++)assertArrayEquals(new float[]{0,0,i+1,0,-1},f.cluts.get(i));
      assertEquals(7,f.renderer.getVsyncCount());
    }
  }

  @Test void callbackHandoffStopsCatchUpAndRestoringTheMarkerDropsMovieDebt() throws Exception {
    try(final var f=new Fixture()) {
      final Runnable[] saved={null}; final int[] game={0},movie={0};
      f.renderer.setSimulationCallback(()->{
        game[0]++;
        if(game[0]==2)saved[0]=f.renderer.setRenderCallback(()->movie[0]++);
      });
      assertTrue(f.renderer.window().simulationConsumesInput()); assertEquals(1,f.present());
      f.now.addAndGet(200_000_000); assertEquals(1,f.present()); assertEquals(2,game[0]);
      assertFalse(f.renderer.window().simulationConsumesInput());
      f.now.addAndGet(30_000_000_000L); f.renderer.setRenderCallback(saved[0]);
      assertTrue(f.renderer.window().simulationConsumesInput()); assertEquals(1,f.present());
      assertEquals(3,game[0]); assertEquals(0,movie[0],"Cinematic callback never executes as a gameplay catch-up tick");
    }
  }

  @Test void pauseAndDebugSingleStepNeverAccumulateAResumeBurst() throws Exception {
    try(final var f=new Fixture()) {
      final int[] ticks={0};f.renderer.setSimulationCallback(()->ticks[0]++); assertEquals(1,f.present());
      set(f.renderer,"togglePause",true); f.now.addAndGet(30_000_000_000L);assertEquals(0,f.present());
      set(f.renderer,"frameAdvanceSingle",true);assertEquals(1,f.present());assertEquals(0,f.present());
      f.now.addAndGet(30_000_000_000L);set(f.renderer,"togglePause",true);assertEquals(1,f.present());
      assertEquals(3,ticks[0]);
    }
  }

  @Test void hardwareOwnsANeutralRateAndRestoresTheSavedGameplayRate() throws Exception {
    final int oldSpeed=legend.core.Config.getGameSpeedMultiplier();
    final int oldInput=GameEngine.PLATFORM.getInputTickRate();
    try(final var f=new Fixture()) {
      legend.core.Config.setGameSpeedMultiplier(16);
      final int[] hardware={0};f.renderer.setSimulationCallback(()->{}); f.renderer.setSimulationRate(960);
      assertEquals(60,f.renderer.window().getFpsLimit());
      final Runnable saved=f.renderer.setHardwareCallback(()->hardware[0]++,30);
      assertEquals(30,f.renderer.window().getFpsLimit()); assertEquals(30,f.renderer.simulationTiming().rate());
      assertEquals(1,f.present()); f.now.addAndGet(100_000_000); assertEquals(3,f.present());
      assertEquals(4,hardware[0]);assertEquals(8,f.renderer.getVsyncCount());
      f.renderer.setSimulationRate(20);assertEquals(20,f.renderer.window().getFpsLimit());
      f.renderer.setRenderCallback(saved);assertEquals(960,f.renderer.simulationTiming().rate());assertEquals(60,f.renderer.window().getFpsLimit());
      final var neutral=RenderEngine.class.getDeclaredMethod("getRenderSpeedMultiplier");neutral.setAccessible(true);
      f.renderer.setRenderCallback(()->{});assertEquals(1,neutral.invoke(f.renderer),"Loading and plain callbacks are neutral");
      GameEngine.PLATFORM.setInputTickRate(960);assertEquals(960,GameEngine.PLATFORM.getInputTickRate());
    } finally { legend.core.Config.setGameSpeedMultiplier(oldSpeed);GameEngine.PLATFORM.setInputTickRate(oldInput); }
  }

  @Test void callbackOwnershipChangesClearEdgesAndPreserveHeldState() throws Exception {
    final var platform=(SdlPlatformManager)GameEngine.PLATFORM;
    final var states=(Map<InputAction,InputActionState>)get(platform,"actionStates");
    final var state=new InputActionState();state.press(); final var action=InputAction.fixed();states.put(action,state);
    try(final var f=new Fixture()) {
      f.renderer.setSimulationCallback(()->{});
      assertFalse(state.isPressed());assertFalse(state.isRepeat());assertTrue(state.isHeld());
    } finally { states.remove(action); }
  }

  @Test void cancellationDiscardsStaleEdgesButNormalQuickTapsRemainLatched() {
    final var state=new InputActionState();
    state.press(); state.repeat(); state.repeat(); state.cancel();
    assertFalse(state.isPressed()); assertFalse(state.isRepeat()); assertFalse(state.isHeld());
    state.press(); state.release(); assertTrue(state.isPressed()); assertTrue(state.isRepeat());
    state.cancel(); assertFalse(state.isPressed()); assertFalse(state.isRepeat());
  }

  @Test void focusReleaseListenersObserveAllInputsAlreadyCancelled() throws Exception {
    final var eventAccess=GameEngine.class.getDeclaredField("EVENT_ACCESS");eventAccess.setAccessible(true);
    ((org.legendofdragoon.modloader.events.EventManager.Access)eventAccess.get(null)).initialize(GameEngine.MODS);
    final var platform=(SdlPlatformManager)GameEngine.PLATFORM;
    final var states=(Map<InputAction,InputActionState>)get(platform,"actionStates");
    final var pressed=(Set<InputAction>)get(platform,"pressed");
    final var first=InputAction.fixed(); final var second=InputAction.fixed(); final var tap=InputAction.fixed();
    for(final var action:List.of(first,second,tap)) {
      final var state=new InputActionState();state.press();state.axis(.75f);
      if(action==tap)state.release();
      states.put(action,state);pressed.add(action);
    }
    final var window=new NoopWindow(new NoopPlatformManager(),640,480);
    final List<InputAction> released=new ArrayList<>();
    window.events().onInputActionReleased((unused,action)->{
      released.add(action);
      for(final var check:List.of(first,second,tap)) {
        assertFalse(platform.isActionPressed(check));assertFalse(platform.isActionRepeat(check));
        assertFalse(platform.isActionHeld(check));assertEquals(0,platform.getAxis(check));
      }
    });
    try {
      final var cancel=SdlPlatformManager.class.getDeclaredMethod("cancelInactiveWindowInput",legend.core.platform.Window.class);
      cancel.setAccessible(true);cancel.invoke(platform,window);
      assertEquals(Set.of(first,second),Set.copyOf(released));
      assertFalse(states.get(tap).isPressed(),"Already released quick taps are cancelled at focus loss");
    } finally { for(final var action:List.of(first,second,tap)){states.remove(action);pressed.remove(action);} }
  }

  @Test void pauseObserversReportTransitionsAndScopedRestorationImmediately() throws Exception {
    try(final var f=new Fixture()) {
      final List<Boolean> states=new ArrayList<>();
      final var previous=f.renderer.setCinematicPauseCallback(states::add);
      f.renderer.setSimulationCallback(()->{}); f.present();
      set(f.renderer,"togglePause",true); f.present(); f.present(); assertTrue(f.renderer.isPaused());
      set(f.renderer,"togglePause",true); f.present(); assertFalse(f.renderer.isPaused());
      assertEquals(List.of(false,true,false),states);
      f.renderer.setCinematicPauseCallback(previous);
    }
  }

  @Test void pressedAndRepeatEdgesAreConsumedOnceWhileHeldAxesRemainAvailable() throws Exception {
    final var platform=(SdlPlatformManager)GameEngine.PLATFORM;
    final var pressed=(Set<InputAction>)get(platform,"pressed");
    final var states=(Map<InputAction,InputActionState>)get(platform,"actionStates");
    final var action=InputAction.fixed(); final var state=new InputActionState();state.axis(.75f);
    states.put(action,state);
    try(final var f=new Fixture()) {
      final int[] edges={0},repeats={0},held={0};
      f.renderer.setSimulationCallback(()->{
        if(platform.isActionPressed(action))edges[0]++;
        if(platform.isActionRepeat(action))repeats[0]++;
        if(platform.isActionHeld(action))held[0]++;
        assertEquals(.75f,platform.getAxis(action));
      });
      state.press();state.axis(.75f);pressed.add(action);
      assertEquals(1,f.present()); f.now.addAndGet(100_000_000); assertEquals(6,f.present());
      assertEquals(1,edges[0]);assertEquals(1,repeats[0]);assertEquals(7,held[0]);
      state.release(); assertFalse(platform.isActionHeld(action));assertFalse(platform.isActionRepeat(action));
    } finally { states.remove(action);pressed.remove(action); }
  }

  @Test void initialDelayedRepeatAndLaterHeldRepeatEachProduceOneConsumablePulse() throws Exception {
    final var state=new InputActionState(); state.press();
    assertTrue(state.isRepeat());state.consumeTick();assertFalse(state.isRepeat());
    state.repeat();state.repeat(); // JUST_PRESSED -> PRESSED -> DELAY, without sleeping.
    set(state,"timestamp",System.nanoTime()-500_000_000L);
    assertTrue(state.repeat());assertTrue(state.isRepeat());
    state.consumeTick();assertFalse(state.isRepeat());state.consumeTick();assertFalse(state.isRepeat());
    set(state,"timestamp",System.nanoTime()-50_000_000L);
    assertFalse(state.repeat());assertFalse(state.isRepeat());
    set(state,"timestamp",System.nanoTime()-50_000_000L);
    assertTrue(state.repeat());assertTrue(state.isRepeat());state.consumeTick();assertFalse(state.isRepeat());
    state.release();assertFalse(state.isHeld());assertFalse(state.isRepeat());
  }

  @Test void pollingCannotExpireAnUnconsumedPressOrRepeatPulse() throws Exception {
    final var state=new InputActionState();state.press();state.repeat();state.repeat();
    assertTrue(state.isPressed());assertTrue(state.isRepeat());
    state.release();assertFalse(state.isHeld());assertTrue(state.isPressed());
    state.consumeTick();assertFalse(state.isPressed());assertFalse(state.isRepeat());
    state.press();state.consumeTick();state.repeat();state.repeat();
    set(state,"timestamp",System.nanoTime()-500_000_000L);assertTrue(state.repeat());
    set(state,"timestamp",System.nanoTime()-50_000_000L);assertFalse(state.repeat());
    assertTrue(state.isRepeat(),"REPEAT -> HELD polling cannot expire an unconsumed pulse");
    state.consumeTick();assertFalse(state.isRepeat());
    set(state,"timestamp",System.nanoTime()-50_000_000L);assertTrue(state.repeat());
    state.release();assertFalse(state.isRepeat(),"Release cancels held-key repetition");
  }
}
