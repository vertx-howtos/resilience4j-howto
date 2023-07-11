package io.vertx.howtos.resilience4j;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class VertxRateLimiterTest {
  private static final int LIMIT = 50;
  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final Duration REFRESH_PERIOD = Duration.ofNanos(500);

  private RateLimiterConfig config;
  private RateLimiter limit;

  @BeforeEach
  public void init() {
    config = RateLimiterConfig.custom()
      .timeoutDuration(TIMEOUT)
      .limitRefreshPeriod(REFRESH_PERIOD)
      .limitForPeriod(LIMIT)
      .build();
    limit = mock(RateLimiter.class);
    when(limit.getRateLimiterConfig()).thenReturn(config);
  }

  @Test
  public void decorateFutureAndSucceed(Vertx vertx, VertxTestContext test) {
    Supplier<String> supplier = mock(Supplier.class);
    when(supplier.get()).thenReturn("Resource");
    Supplier<Future<String>> future = () -> {
      return vertx.executeBlocking(promise -> {
        promise.complete(supplier.get());
      });
    };
    Supplier<Future<String>> decorated = VertxRateLimiter.decorateFuture(limit, future, vertx);

    when(limit.reservePermission(1)).thenReturn(0L);
    Future<String> success = decorated.get();

    success.onComplete(test.succeeding(result -> {
      assertThat(result).isEqualTo("Resource");
      verify(supplier).get();
      test.completeNow();
    }));
  }

  @Test
  public void decorateFutureAndFail(Vertx vertx, VertxTestContext test) {
    Supplier<String> supplier = mock(Supplier.class);
    when(supplier.get()).thenReturn("Resource");
    Supplier<Future<String>> future = () -> {
      return vertx.executeBlocking(promise -> {
        promise.complete(supplier.get());
      });
    };
    Supplier<Future<String>> decorated = VertxRateLimiter.decorateFuture(limit, future, vertx);

    when(limit.reservePermission(1)).thenReturn(-1L);
    Future<String> notPermittedFuture = decorated.get();

    notPermittedFuture.onComplete(test.failing(error -> {
      assertThat(error).isExactlyInstanceOf(RequestNotPermitted.class);
      verify(supplier, never()).get();
      test.completeNow();
    }));
  }
}
