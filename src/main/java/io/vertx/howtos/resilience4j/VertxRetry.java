package io.vertx.howtos.resilience4j;

import io.github.resilience4j.retry.Retry;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;

import java.util.function.Supplier;

public class VertxRetry {
  public static <T> Future<T> executeFuture(Retry retry, Vertx vertx, Supplier<Future<T>> supplier) {
    ContextInternal ctx = ContextInternal.current();
    Promise<T> promise = ctx != null ? ctx.promise() : Promise.promise();

    new AsyncRetryBlock<>(vertx, retry.asyncContext(), supplier, promise).run();

    return promise.future();
  }

  public static <T> Supplier<Future<T>> decorateFuture(Retry retry, Vertx vertx, Supplier<Future<T>> supplier) {
    return () -> executeFuture(retry, vertx, supplier);
  }

  private static class AsyncRetryBlock<T> implements Runnable, Handler<Long> {
    private final Vertx vertx;
    private final Retry.AsyncContext<T> retryContext;
    private final Supplier<Future<T>> supplier;
    private final Promise<T> promise;

    AsyncRetryBlock(Vertx vertx, Retry.AsyncContext<T> retryContext, Supplier<Future<T>> supplier, Promise<T> promise) {
      this.vertx = vertx;
      this.retryContext = retryContext;
      this.supplier = supplier;
      this.promise = promise;
    }

    @Override
    public void run() {
      try {
        supplier.get().onComplete(result -> {
          if (result.failed()) {
            if (result.cause() instanceof Exception) {
              onError((Exception) result.cause());
            } else {
              promise.fail(result.cause());
            }
          } else {
            onResult(result.result());
          }
        });
      } catch (Exception e) {
        onError(e);
      }
    }

    @Override
    public void handle(Long ignored) {
      run();
    }

    private void onError(Exception t) {
      long delay = retryContext.onError(t);
      if (delay < 1) {
        promise.fail(t);
      } else {
        vertx.setTimer(delay, this);
      }
    }

    private void onResult(T result) {
      long delay = retryContext.onResult(result);
      if (delay < 1) {
        try {
          retryContext.onComplete();
          promise.complete(result);
        } catch (Exception e) {
          promise.fail(e);
        }
      } else {
        vertx.setTimer(delay, this);
      }
    }
  }
}
