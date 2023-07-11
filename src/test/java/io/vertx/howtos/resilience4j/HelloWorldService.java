package io.vertx.howtos.resilience4j;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.function.Supplier;

public interface HelloWorldService {
  String returnHelloWorld();

  static Supplier<Future<String>> runSync(HelloWorldService helloWorldService) {
    return () -> {
      return Future.future(promise -> {
        promise.complete(helloWorldService.returnHelloWorld());
      });
    };
  }

  static Supplier<Future<String>> runAsync(Vertx vertx, HelloWorldService helloWorldService) {
    return () -> {
      return vertx.executeBlocking(promise -> {
        promise.complete(helloWorldService.returnHelloWorld());
      });
    };
  }
}
