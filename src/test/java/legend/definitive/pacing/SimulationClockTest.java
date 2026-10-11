package legend.definitive.pacing;

import legend.core.renderer.SimulationClock;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

final class SimulationClockTest {
  @Test void allGameplayStatesAndSpeedsKeepTheirTickBudgetAtEveryPresentationRate() {
    for(final int base : new int[]{20,30,60}) for(int speed=1;speed<=16;speed++) {
      for(final int presentation : new int[]{15,30,40,60,120,1000}) {
        final var now=new AtomicLong(); final var clock=new SimulationClock(now::get);
        final int hz=base*speed; clock.setRate(hz); clock.reset(false);
        final int[] ticks={0};
        for(int frame=1;frame<=presentation;frame++) {
          now.set(frame*1_000_000_000L/presentation);
          clock.advance(()->ticks[0]++);
          clock.setRate(hz);
        }
        assertEquals(hz,ticks[0],"Simulation "+hz+" Hz at presentation "+presentation+" Hz");
        assertEquals(0,clock.snapshot().droppedNanos());
        assertFalse(clock.snapshot().budgetLimited());
      }
    }
  }

  @Test void slowHardwareRatesAccumulateACompleteIntervalWithoutFreezing() {
    for(final int hz : new int[]{1,2,3,4,10}) for(final int presentation : new int[]{15,30,40,60,120,1000}) {
      final var now=new AtomicLong(); final var clock=new SimulationClock(now::get);
      clock.setRate(hz); clock.reset(false); final int[] ticks={0};
      for(int frame=1;frame<=presentation;frame++) {
        now.set(frame*1_000_000_000L/presentation); clock.advance(()->ticks[0]++);
      }
      assertEquals(hz,ticks[0],"Hardware "+hz+" Hz at presentation "+presentation+" Hz");
      assertEquals(0,clock.snapshot().droppedNanos());
    }
    for(final int hz : new int[]{1,2,3}) {
      final var now=new AtomicLong(); final var clock=new SimulationClock(now::get);
      clock.setRate(hz); clock.reset(false); now.set(30_000_000_000L);
      assertEquals(1,clock.advance(()->{}),"Slow hardware suspend recovery remains bounded to one interval");
      assertEquals(30_000_000_000L-1_000_000_000L/hz-SimulationClock.MAX_DEBT_NANOS,clock.snapshot().droppedNanos());
      assertEquals(SimulationClock.MAX_DEBT_NANOS,clock.snapshot().pendingNanos());
    }
  }

  @Test void phaseSurvivesRepeatedAssignmentsAndVariableUncappedPresentation() {
    final var now=new AtomicLong(); final var clock=new SimulationClock(now::get);
    clock.setRate(60); clock.reset(false); final int[] ticks={0};
    final long[] intervals={100,1_000,123_456,8_333_333,7_999_999,33_333_333}; int n=0;
    while(now.get()<1_000_000_000L) {
      now.set(Math.min(1_000_000_000L,now.get()+intervals[n++%intervals.length]));
      clock.advance(()->ticks[0]++); clock.setRate(60);
    }
    assertEquals(60,ticks[0]);
  }

  @Test void handoffsInsideCatchUpStopTheOldDomainImmediately() {
    final var now=new AtomicLong(); final var clock=new SimulationClock(now::get);
    clock.setRate(960); clock.reset(false); now.set(200_000_000);
    final int[] ticks={0};
    assertEquals(1,clock.advance(()->{ticks[0]++;clock.setRate(20);}));
    assertEquals(1,ticks[0]); assertEquals(0,clock.advance(()->fail("Old backlog leaked")));
    now.addAndGet(50_000_000); assertEquals(1,clock.advance(()->ticks[0]++));
    now.addAndGet(200_000_000);
    assertEquals(1,clock.advance(()->clock.reset(false)),"Callback replacement cancels remaining debt even at the same rate");
  }

  @Test void suspendedClockRecoveryAndOverloadAreBoundedAndMeasurable() {
    final var now=new AtomicLong(); final var clock=new SimulationClock(now::get);
    clock.setRate(960); clock.reset(false); now.set(30_000_000_000L);
    assertEquals(240,clock.advance(()->{}));
    assertEquals(29_750_000_000L,clock.snapshot().droppedNanos());
    clock.reset(false); now.addAndGet(200_000_000);
    assertEquals(5,clock.advance(()->now.addAndGet(10_000_000)));
    assertTrue(clock.snapshot().budgetLimited());
    assertTrue(clock.snapshot().pendingNanos()>0);
    clock.reset(false); assertEquals(0,clock.snapshot().pendingNanos());
    now.addAndGet(-1); assertEquals(0,clock.advance(()->fail("Negative clock movement advanced simulation")));
    assertEquals(0,clock.snapshot().pendingNanos());
    assertThrows(IllegalArgumentException.class,()->clock.setRate(0));
    assertThrows(IllegalArgumentException.class,()->clock.setRate(961));
  }

  @Test void monotonicWrapRetainsValidElapsedTime() {
    final var now=new AtomicLong(Long.MAX_VALUE-10_000_000);
    final var clock=new SimulationClock(now::get); clock.setRate(60); clock.reset(false);
    now.addAndGet(20_000_000); assertEquals(1,clock.advance(()->{}));
    assertEquals(0,clock.snapshot().droppedNanos());
  }
}
