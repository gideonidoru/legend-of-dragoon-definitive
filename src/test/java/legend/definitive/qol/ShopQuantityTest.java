package legend.definitive.qol;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ShopQuantityTest {
  @Test void affordabilityRespectsPriceCapacityAndFreeGoods() {
    assertEquals(3, ShopQuantity.affordable(35, 10, 20));
    assertEquals(2, ShopQuantity.affordable(35, 10, 2));
    assertEquals(99, ShopQuantity.affordable(0, 0, 200));
    assertEquals(0, ShopQuantity.affordable(100, -1, 200));
    assertEquals(0, ShopQuantity.affordable(100, 1, -1));
  }

  @Test void cancelDoesNotInvokeEventsOrMutateGold() {
    final AtomicInteger gold = new AtomicInteger(100);
    final int completed = ShopQuantity.purchase(0, 10, gold::get,
      () -> fail("cancel invoked purchase event"), () -> { fail("cancel gave inventory"); return true; }, gold::addAndGet);
    assertEquals(0, completed);
    assertEquals(100, gold.get());
  }

  @Test void failedGiveStopsAndChargesOnlySuccessfulUnits() {
    final AtomicInteger gold = new AtomicInteger(100);
    final AtomicInteger gives = new AtomicInteger();
    final AtomicInteger events = new AtomicInteger();
    assertEquals(2, ShopQuantity.purchase(5, 10, gold::get, events::incrementAndGet,
      () -> gives.incrementAndGet() <= 2, cost -> gold.addAndGet(-cost)));
    assertEquals(80, gold.get());
    assertEquals(3, events.get());
  }

  @Test void eventChangedBudgetCannotCreateUnaffordablePurchase() {
    final AtomicInteger gold = new AtomicInteger(10);
    assertEquals(0, ShopQuantity.purchase(1, 10, gold::get, () -> gold.set(0),
      () -> { fail("gave item after gold changed"); return true; }, cost -> gold.addAndGet(-cost)));
  }

  @Test void purchaseStopsAtGoldBoundaryAndGoldSaturatesWithoutOverflow() {
    final AtomicInteger gold = new AtomicInteger(25);
    assertEquals(2, ShopQuantity.purchase(99, 10, gold::get, () -> { }, () -> true, cost -> gold.addAndGet(-cost)));
    assertEquals(5, gold.get());
    assertEquals(99_999_999, ShopQuantity.addGold(99_999_998, Integer.MAX_VALUE));
  }
}
