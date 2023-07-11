package io.vertx.howtos.resilience4j;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class VertxRateLimiter {
  public static <T> Future<T> executeFuture(RateLimiter rateLimiter, Supplier<Future<T>> supplier, Vertx vertx) {
    return executeFuture(rateLimiter, 1, supplier, vertx);
  }

  public static <T> Future<T> executeFuture(RateLimiter rateLimiter, int permits, Supplier<Future<T>> supplier, Vertx vertx) {
    ContextInternal ctx = ContextInternal.current();
    Promise<T> promise = ctx != null ? ctx.promise() : Promise.promise();

    long delay = rateLimiter.reservePermission(permits); // result is in nanoseconds
    if (delay < 0) {
      promise.fail(RequestNotPermitted.createRequestNotPermitted(rateLimiter));
    } else if (delay == 0) {
      invokePermitted(promise, rateLimiter, supplier);
    } else {
      vertx.setTimer(TimeUnit.NANOSECONDS.toMillis(delay), ignored -> {
        invokePermitted(promise, rateLimiter, supplier);
      });
    }

    return promise.future();
  }

  public static <T> Supplier<Future<T>> decorateFuture(RateLimiter rateLimiter, Supplier<Future<T>> supplier, Vertx vertx) {
    return decorateFuture(rateLimiter, 1, supplier, vertx);
  }

  public static <T> Supplier<Future<T>> decorateFuture(RateLimiter rateLimiter, int permits, Supplier<Future<T>> supplier, Vertx vertx) {
    return () -> executeFuture(rateLimiter, permits, supplier, vertx);
  }

  private static <T> void invokePermitted(Promise<T> promise, RateLimiter rateLimiter, Supplier<Future<T>> supplier) {
    try {
      supplier.get().onComplete(result -> {
        if (result.failed()) {
          rateLimiter.onError(result.cause());
          promise.fail(result.cause());
        } else {
          rateLimiter.onResult(result.result());
          promise.complete(result.result());
        }
      });
    } catch (Exception e) {
      rateLimiter.onError(e);
      promise.fail(e);
    }
  }
}
