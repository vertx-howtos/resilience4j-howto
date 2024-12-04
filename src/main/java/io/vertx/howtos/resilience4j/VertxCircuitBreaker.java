package io.vertx.howtos.resilience4j;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.internal.ContextInternal;

import java.util.function.Supplier;

public class VertxCircuitBreaker {
  public static <T> Future<T> executeFuture(CircuitBreaker circuitBreaker, Supplier<Future<T>> supplier) {
    ContextInternal ctx = ContextInternal.current();
    Promise<T> promise = ctx != null ? ctx.promise() : Promise.promise();

    if (!circuitBreaker.tryAcquirePermission()) {
      promise.fail(CallNotPermittedException.createCallNotPermittedException(circuitBreaker));
    } else {
      long start = circuitBreaker.getCurrentTimestamp();
      try {
        supplier.get().onComplete(result -> {
          long duration = circuitBreaker.getCurrentTimestamp() - start;
          if (result.failed()) {
            circuitBreaker.onError(duration, circuitBreaker.getTimestampUnit(), result.cause());
            promise.fail(result.cause());
          } else {
            circuitBreaker.onResult(duration, circuitBreaker.getTimestampUnit(), result.result());
            promise.complete(result.result());
          }
        });
      } catch (Exception exception) {
        long duration = circuitBreaker.getCurrentTimestamp() - start;
        circuitBreaker.onError(duration, circuitBreaker.getTimestampUnit(), exception);
        promise.fail(exception);
      }
    }

    return promise.future();
  }

  public static <T> Supplier<Future<T>> decorateFuture(CircuitBreaker circuitBreaker, Supplier<Future<T>> supplier) {
    return () -> executeFuture(circuitBreaker, supplier);
  }
}
