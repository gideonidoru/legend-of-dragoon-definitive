package legend.definitive.qol;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdditionFeedbackTest {
  @Test void observesExistingResolutionWithoutChangingIt() {
    final AdditionFeedback feedback = new AdditionFeedback();
    final int[] completion = {-1, -2, -3, 1};
    final AdditionFeedback.Result[] results = {AdditionFeedback.Result.EARLY, AdditionFeedback.Result.LATE, AdditionFeedback.Result.WRONG_BUTTON, AdditionFeedback.Result.SUCCESS};
    for(int i = 0; i < completion.length; i++) {
      feedback.record(completion[i], 10, 10, 12, false);
      assertEquals(results[i], feedback.visibleResult());
    }
    assertArrayEquals(new int[]{-1, -2, -3, 1}, completion);
    feedback.record(1, 11, 10, 12, false);
    assertEquals(AdditionFeedback.Result.PERFECT, feedback.visibleResult());
  }

  @Test void automaticAndUnattemptedHitsProduceNoTrainingResult() {
    final AdditionFeedback feedback = new AdditionFeedback();
    feedback.record(1, 11, 10, 12, true);
    feedback.record(0, 11, 10, 12, false);
    assertNull(feedback.visibleResult());
  }

  @Test void evenWindowsUseEarlierCentreAndFeedbackExpiresOrResets() {
    final AdditionFeedback feedback = new AdditionFeedback();
    feedback.record(1, 10, 10, 11, false);
    assertEquals(AdditionFeedback.Result.PERFECT, feedback.visibleResult());
    for(int i = 0; i < 23; i++) feedback.tick();
    assertNotNull(feedback.visibleResult());
    feedback.tick();
    assertNull(feedback.visibleResult());
    feedback.record(-2, 15, 10, 12, false);
    feedback.clear();
    assertNull(feedback.visibleResult());
  }
}
