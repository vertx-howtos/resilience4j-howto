package io.vertx.howtos.resilience4j;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.ContextInternal;

import java.util.function.Supplier;

public class VertxBulkhead {
  public static <T> Future<T> executeFuture(Bulkhead bulkhead, Supplier<Future<T>> supplier) {
    ContextInternal ctx = ContextInternal.current();
    Promise<T> promise = ctx != null ? ctx.promise() : Promise.promise();

    if (!bulkhead.tryAcquirePermission()) {
      promise.fail(BulkheadFullException.createBulkheadFullException(bulkhead));
    } else {
      try {
        supplier.get().onComplete(result -> {
          bulkhead.onComplete();
          if (result.failed()) {
            promise.fail(result.cause());
          } else {
            promise.complete(result.result());
          }
        });
      } catch (Exception throwable) {
        bulkhead.onComplete();
        promise.fail(throwable);
      }
    }

    return promise.future();
  }

  public static <T> Supplier<Future<T>> decorateFuture(Bulkhead bulkhead, Supplier<Future<T>> supplier) {
    return () -> executeFuture(bulkhead, supplier);
  }
}
