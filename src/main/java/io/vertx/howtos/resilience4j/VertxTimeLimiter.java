package io.vertx.howtos.resilience4j;

import io.github.resilience4j.timelimiter.TimeLimiter;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;

import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public class VertxTimeLimiter {
  public static <T> Future<T> executeFuture(TimeLimiter timeLimiter, Vertx vertx, Supplier<Future<T>> supplier) {
    ContextInternal ctx = ContextInternal.current();
    Promise<T> promise = ctx != null ? ctx.promise() : Promise.promise();

    long timerId = vertx.setTimer(timeLimiter.getTimeLimiterConfig().getTimeoutDuration().toMillis(), ignored -> {
      TimeoutException exception = TimeLimiter.createdTimeoutExceptionWithName(timeLimiter.getName(), null);
      if (promise.tryFail(exception)) {
        timeLimiter.onError(exception);
      }
    });

    try {
      supplier.get().onComplete(result -> {
        vertx.cancelTimer(timerId);
        if (result.succeeded() && promise.tryComplete(result.result())) {
          timeLimiter.onSuccess();
        }
        if (result.failed() && promise.tryFail(result.cause())) {
          timeLimiter.onError(result.cause());
        }
      });
    } catch (Exception e) {
      vertx.cancelTimer(timerId);
      if (promise.tryFail(e)) {
        timeLimiter.onError(e);
      }
    }

    return promise.future();
  }

  public static <T> Supplier<Future<T>> decorateFuture(TimeLimiter timeLimiter, Vertx vertx, Supplier<Future<T>> supplier) {
    return () -> executeFuture(timeLimiter, vertx, supplier);
  }
}
