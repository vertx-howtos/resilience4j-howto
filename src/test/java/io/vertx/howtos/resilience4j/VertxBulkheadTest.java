package io.vertx.howtos.resilience4j;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class VertxBulkheadTest {
  private HelloWorldService helloWorldService;
  private BulkheadConfig config;

  @BeforeEach
  public void setUp() {
    helloWorldService = mock(HelloWorldService.class);
    config = BulkheadConfig.custom()
      .maxConcurrentCalls(1)
      .build();
  }

  @Test
  public void shouldDecorateFutureAndReturnWithSuccess(Vertx vertx, VertxTestContext test) {
    Bulkhead bulkhead = Bulkhead.of("test", config);
    when(helloWorldService.returnHelloWorld()).thenReturn("Hello");
    Supplier<Future<String>> futureSupplier = HelloWorldService.runAsync(vertx, helloWorldService);
    Supplier<Future<String>> decoratedFutureSupplier = VertxBulkhead.decorateFuture(bulkhead, futureSupplier);

    Future<String> decoratedFuture = decoratedFutureSupplier.get().map(value -> value + " world");

    decoratedFuture.onComplete(test.succeeding(result -> {
      assertThat(result).isEqualTo("Hello world");
      assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(1);
      verify(helloWorldService).returnHelloWorld();
      test.completeNow();
    }));
  }

  @Test
  public void shouldExecuteFutureAndReturnWithSuccess(Vertx vertx, VertxTestContext test) {
    Bulkhead bulkhead = Bulkhead.of("test", config);
    when(helloWorldService.returnHelloWorld()).thenReturn("Hello");

    Future<String> decoratedFuture = VertxBulkhead.executeFuture(bulkhead, HelloWorldService.runAsync(vertx, helloWorldService))
      .map(value -> value + " world");

    decoratedFuture.onComplete(test.succeeding(result -> {
      assertThat(result).isEqualTo("Hello world");
      assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(1);
      verify(helloWorldService).returnHelloWorld();
      test.completeNow();
    }));
  }

  @Test
  public void shouldDecorateFutureAndReturnWithSynchronousException(Vertx vertx, VertxTestContext test) {
    Bulkhead bulkhead = Bulkhead.of("test", config);
    Supplier<Future<String>> futureSupplier = () -> {
      throw new HelloWorldException();
    };
    Supplier<Future<String>> decoratedFutureSupplier = VertxBulkhead.decorateFuture(bulkhead, futureSupplier);

    Future<String> decoratedFuture = decoratedFutureSupplier.get();

    decoratedFuture.onComplete(test.failing(error -> {
      assertThat(error).isInstanceOf(HelloWorldException.class);
      verify(helloWorldService, never()).returnHelloWorld();
      assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(1);
      test.completeNow();
    }));
  }

  @Test
  public void shouldDecorateFutureAndReturnWithAsynchronousException(Vertx vertx, VertxTestContext test) {
    Bulkhead bulkhead = Bulkhead.of("test", config);
    when(helloWorldService.returnHelloWorld()).thenThrow(new RuntimeException("Asynchronous exception"));
    Supplier<Future<String>> futureSupplier = HelloWorldService.runAsync(vertx, helloWorldService);
    Supplier<Future<String>> decoratedFutureSupplier = VertxBulkhead.decorateFuture(bulkhead, futureSupplier);

    Future<String> decoratedFuture = decoratedFutureSupplier.get();

    decoratedFuture.onComplete(test.failing(error -> {
      assertThat(error)
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Asynchronous exception");
      verify(helloWorldService).returnHelloWorld();
      assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(1);
      test.completeNow();
    }));
  }
}
