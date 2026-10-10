package legend.definitive.pacing;

import legend.core.audio.GenericSource;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.legendofdragoon.dabas.core.sound.Spu.SAMPLES_PER_TICK;

final class DabasAudioTest {
  private static final class Source extends GenericSource {
    int free=16,generation=1;boolean available=true;
    final List<byte[]> output=new ArrayList<>();
    Source() { super(0,44100); }
    @Override public synchronized int generation() { return this.generation; }
    @Override public synchronized boolean outputAvailable() { return this.available; }
    @Override public synchronized int availableBuffers() { return this.free; }
    @Override public synchronized void bufferOutput(ByteBuffer data) { final byte[] copy=new byte[data.remaining()];data.get(copy);this.output.add(copy);this.free--; }
  }
  private static Object pump(Source source) throws Exception {
    final var c=Class.forName("legend.game.dabas.DabasAudio").getDeclaredConstructor(GenericSource.class);c.setAccessible(true);return c.newInstance(source);
  }
  private static Object call(Object pump,String name,Class<?>[] args,Object... values) throws Exception {
    final var m=pump.getClass().getDeclaredMethod(name,args);m.setAccessible(true);return m.invoke(pump,values);
  }
  private static void call(Object pump,String name) throws Exception { call(pump,name,new Class<?>[0]); }
  private static void submit(Object pump,byte[] data) throws Exception { call(pump,"submit",new Class<?>[]{byte[].class},data); }

  @Test void producerBackpressurePreservesOrderOwnershipAndOneFreeOutputBuffer() throws Exception {
    final var source=new Source();final var pump=pump(source);
    final AtomicBoolean completed=new AtomicBoolean();final var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
    final var producer=new Thread(()->{
      try { for(int i=0;i<25;i++){final byte[] samples=new byte[SAMPLES_PER_TICK];samples[0]=(byte)i;submit(pump,samples);samples[0]=99;}completed.set(true); }
      catch(Throwable e){failure.set(e);}
    });producer.start();
    try {
      final long deadline=System.nanoTime()+2_000_000_000L;
      while((int)call(pump,"pendingBlocks",new Class<?>[0])<4&&System.nanoTime()<deadline)Thread.sleep(1);
      assertEquals(4,call(pump,"pendingBlocks",new Class<?>[0]));assertFalse(completed.get());
      synchronized(source){assertEquals(1,source.free);source.free=16;}
      call(pump,"drain");producer.join(2000);assertFalse(producer.isAlive());assertNull(failure.get());assertTrue(completed.get());
      synchronized(source){source.free=16;}call(pump,"drain");
      assertEquals(25,source.output.size());for(int i=0;i<25;i++)assertEquals((byte)i,source.output.get(i)[0]);
    } finally { call(pump,"close");producer.join(2000); }
  }

  @Test void pauseBackpressuresTheIndependentTimerAndResumePreservesItsBlock() throws Exception {
    final var source=new Source();final var pump=pump(source);
    call(pump,"setPaused",new Class<?>[]{boolean.class},true);
    final var started=new java.util.concurrent.CountDownLatch(1);final var done=new AtomicBoolean();
    final var producer=new Thread(()->{try{started.countDown();final byte[] samples=new byte[SAMPLES_PER_TICK];samples[0]=42;submit(pump,samples);done.set(true);}catch(Exception e){throw new RuntimeException(e);}});
    producer.start();started.await();
    try {
      final long deadline=System.nanoTime()+2_000_000_000L;
      while(producer.getState()!=Thread.State.TIMED_WAITING&&System.nanoTime()<deadline)Thread.sleep(1);
      assertFalse(done.get());assertTrue(source.output.isEmpty());
      call(pump,"setPaused",new Class<?>[]{boolean.class},false);producer.join(2000);
      assertFalse(producer.isAlive());assertTrue(done.get());assertEquals(42,source.output.getFirst()[0]);
    } finally { call(pump,"close");producer.join(2000); }
  }

  @Test void closeAndDeviceReplacementCancelPendingAudioAndWakeTheProducer() throws Exception {
    final var source=new Source();source.free=1;final var pump=pump(source);
    for(int i=0;i<4;i++)submit(pump,new byte[SAMPLES_PER_TICK]);
    final var producer=new Thread(()->{try{submit(pump,new byte[SAMPLES_PER_TICK]);}catch(Exception e){throw new RuntimeException(e);}});
    producer.start();call(pump,"close");producer.join(2000);assertFalse(producer.isAlive());assertEquals(0,call(pump,"pendingBlocks",new Class<?>[0]));
    submit(pump,new byte[SAMPLES_PER_TICK]);assertTrue(source.output.isEmpty());
    final var next=pump(source);submit(next,new byte[SAMPLES_PER_TICK]);source.generation++;call(next,"drain");
    assertEquals(0,call(next,"pendingBlocks",new Class<?>[0]));source.available=false;submit(next,new byte[SAMPLES_PER_TICK]);assertEquals(0,call(next,"pendingBlocks",new Class<?>[0]));call(next,"close");
  }
}
