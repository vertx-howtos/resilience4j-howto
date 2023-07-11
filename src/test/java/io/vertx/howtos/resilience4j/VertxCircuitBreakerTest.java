package io.vertx.howtos.resilience4j;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class VertxCircuitBreakerTest {
  private HelloWorldService helloWorldService;

  @BeforeEach
  public void setUp() {
    helloWorldService = mock(HelloWorldService.class);
  }

  @Test
  public void shouldDecorateFutureAndReturnWithSuccess(Vertx vertx, VertxTestContext test) {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("backendName");
    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    when(helloWorldService.returnHelloWorld()).thenReturn("Hello");
    Supplier<Future<String>> futureSupplier = HelloWorldService.runAsync(vertx, helloWorldService);
    Supplier<Future<String>> decoratedFutureSupplier = VertxCircuitBreaker.decorateFuture(circuitBreaker, futureSupplier);

    Future<String> decoratedFuture = decoratedFutureSupplier.get().map(value -> value + " world");

    decoratedFuture.onComplete(test.succeeding(result -> {
      assertThat(result).isEqualTo("Hello world");
      verify(helloWorldService).returnHelloWorld();
      CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
      assertThat(metrics.getNumberOfBufferedCalls()).isEqualTo(1);
      assertThat(metrics.getNumberOfFailedCalls()).isZero();
      test.completeNow();
    }));
  }

  @Test
  public void shouldExecuteFutureAndReturnWithSuccess(Vertx vertx, VertxTestContext test) {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("backendName");
    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    when(helloWorldService.returnHelloWorld()).thenReturn("Hello");

    Future<String> decoratedFuture = VertxCircuitBreaker.executeFuture(circuitBreaker, HelloWorldService.runAsync(vertx, helloWorldService))
      .map(value -> value + " world");

    decoratedFuture.onComplete(test.succeeding(result -> {
      assertThat(result).isEqualTo("Hello world");
      verify(helloWorldService).returnHelloWorld();
      CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
      assertThat(metrics.getNumberOfBufferedCalls()).isEqualTo(1);
      assertThat(metrics.getNumberOfFailedCalls()).isZero();
      test.completeNow();
    }));
  }

  @Test
  public void shouldDecorateFutureAndReturnWithSynchronousException(Vertx vertx, VertxTestContext test) {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("backendName");
    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    Supplier<Future<String>> futureSupplier = () -> {
      throw new RuntimeException("Synchronous exception");
    };
    Supplier<Future<String>> decoratedFutureSupplier = VertxCircuitBreaker.decorateFuture(circuitBreaker, futureSupplier);

    Future<String> decoratedFuture = decoratedFutureSupplier.get();

    decoratedFuture.onComplete(test.failing(error -> {
      assertThat(error)
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Synchronous exception");
      CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
      assertThat(metrics.getNumberOfBufferedCalls()).isEqualTo(1);
      assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(1);
      test.completeNow();
    }));
  }

  @Test
  public void shouldDecorateFutureAndReturnWithAsynchronousException(Vertx vertx, VertxTestContext test) {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("backendName");
    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    when(helloWorldService.returnHelloWorld()).thenThrow(new RuntimeException("Asynchronous exception"));
    Supplier<Future<String>> futureSupplier = HelloWorldService.runAsync(vertx, helloWorldService);
    Supplier<Future<String>> decoratedFutureSupplier = VertxCircuitBreaker.decorateFuture(circuitBreaker, futureSupplier);

    Future<String> decoratedFuture = decoratedFutureSupplier.get();

    decoratedFuture.onComplete(test.failing(error -> {
      assertThat(error)
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Asynchronous exception");
      verify(helloWorldService).returnHelloWorld();
      CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
      assertThat(metrics.getNumberOfBufferedCalls()).isEqualTo(1);
      assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(1);
      test.completeNow();
    }));
  }

  @Test
  public void shouldDecorateFutureAndIgnoreHelloWorldException(Vertx vertx, VertxTestContext test) {
    CircuitBreakerConfig config = CircuitBreakerConfig.custom()
      .ignoreExceptions(HelloWorldException.class)
      .build();
    CircuitBreaker circuitBreaker = CircuitBreaker.of("backendName", config);
    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    when(helloWorldService.returnHelloWorld()).thenThrow(new HelloWorldException());
    Supplier<Future<String>> futureSupplier = HelloWorldService.runAsync(vertx, helloWorldService);

    Future<String> future = VertxCircuitBreaker.executeFuture(circuitBreaker, futureSupplier);

    future.onComplete(test.failing(error -> {
      assertThat(error).isInstanceOf(HelloWorldException.class);
      verify(helloWorldService).returnHelloWorld();
      CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
      assertThat(metrics.getNumberOfBufferedCalls()).isZero();
      assertThat(metrics.getNumberOfFailedCalls()).isZero();
      test.completeNow();
    }));
  }
}
