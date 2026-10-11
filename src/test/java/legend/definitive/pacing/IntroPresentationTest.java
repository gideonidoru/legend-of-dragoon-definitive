package legend.definitive.pacing;

import legend.core.IntroPresentation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class IntroPresentationTest {
  @Test void equalElapsedTimeProducesEqualFadesAndHueAcrossPresentationCaps() {
    final var reference=new IntroPresentation();reference.reset(0);reference.advance(0,true,true);reference.advance(10_000_000_000L,true,true);
    for(int fps:new int[]{15,30,40,60,120,1000}) {
      final var intro=new IntroPresentation();intro.reset(0);intro.advance(0,true,true);
      for(int frame=1;frame<=fps*10;frame++)intro.advance(frame*1_000_000_000L/fps,true,true);
      assertEquals(reference.eyeFade(),intro.eyeFade());assertEquals(reference.loadingFade(),intro.loadingFade());
      assertEquals(reference.hue(),intro.hue(),0.000001,"Hue at "+fps+" Hz");
    }
    assertEquals(1,reference.eyeFade());assertEquals(1,reference.loadingFade());
  }

  @Test void irregularCallbacksAndLargeStallsDoNotLeaveAnInvisibleLoadingEye() {
    final var intro=new IntroPresentation();intro.reset(0);intro.advance(0,true,false);
    intro.advance(100_000_000,true,false);assertEquals(.03f,intro.eyeFade(),.000001);
    intro.advance(500_000_000,true,false);assertEquals(.15f,intro.eyeFade(),.000001);assertEquals(0,intro.loadingFade());
    intro.advance(30_000_000_000L,true,true);assertEquals(1,intro.eyeFade());assertEquals(0,intro.loadingFade(),"Prior cinematic time is not a loading fade");
    intro.advance(30_500_000_000L,true,true);assertEquals(.6f,intro.loadingFade(),.000001);
    intro.advance(31_000_000_000L,true,true);assertEquals(1,intro.loadingFade());
    final float hue=intro.hue();intro.advance(32_000_000_000L,false,true);intro.advance(33_000_000_000L,false,true);assertEquals(hue,intro.hue());
    intro.skip();intro.advance(34_000_000_000L,true,true);assertEquals(1,intro.eyeFade());assertEquals(1,intro.loadingFade());
  }

  @Test void resetNegativeTimeAndMonotonicWrapKeepAValidNeutralClock() {
    final var intro=new IntroPresentation();intro.reset(Long.MAX_VALUE-50_000_000);
    intro.advance(Long.MIN_VALUE+49_999_999,true,true);assertEquals(.03f,intro.eyeFade(),.000001);
    intro.reset(100);intro.advance(99,true,true);assertEquals(0,intro.eyeFade());assertEquals(0,intro.loadingFade());
  }
}
