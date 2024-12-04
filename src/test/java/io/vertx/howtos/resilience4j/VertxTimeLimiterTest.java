package io.vertx.howtos.resilience4j;

import io.github.resilience4j.timelimiter.TimeLimiter;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class VertxTimeLimiterTest {
  @Test
  public void shouldFailWithTimeoutException(Vertx vertx, VertxTestContext test) {
    Duration timeoutDuration = Duration.ofMillis(100);
    TimeLimiter timeLimiter = TimeLimiter.of(timeoutDuration);

    Supplier<Future<Integer>> supplier = () -> vertx.executeBlocking(() -> {
      try {
        // sleep for timeout
        Thread.sleep(5000);
      } catch (InterruptedException e) {
        // nothing
      }
      return 0;
    });

    Future<Integer> decorated = VertxTimeLimiter.decorateFuture(timeLimiter, vertx, supplier).get();

    decorated.onComplete(test.failing(error -> {
      assertThat(error).isInstanceOf(TimeoutException.class);
      test.completeNow();
    }));
  }

  @Test
  public void shouldCompleteWithResult(Vertx vertx, VertxTestContext test) {
    Duration timeoutDuration = Duration.ofSeconds(5);
    TimeLimiter timeLimiter = TimeLimiter.of(timeoutDuration);

    Supplier<Future<Integer>> supplier = () -> vertx.executeBlocking(() -> {
      try {
        // sleep but not timeout
        Thread.sleep(100);
      } catch (InterruptedException e) {
        // nothing
      }
      return 42;
    });

    Future<Integer> future = VertxTimeLimiter.executeFuture(timeLimiter, vertx, supplier);

    future.onComplete(test.succeeding(result -> {
      assertThat(result).isEqualTo(42);
      test.completeNow();
    }));
  }
}
