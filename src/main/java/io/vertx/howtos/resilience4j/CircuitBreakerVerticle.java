package io.vertx.howtos.resilience4j;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;

// tag::class[]
public class CircuitBreakerVerticle extends VerticleBase {
// end::class[]
  // tag::main[]
  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new CircuitBreakerVerticle());
  }
  // end::main[]

  // tag::start[]
  @Override
  public Future<?> start() {
    // tag::circuit-breaker[]
    CircuitBreaker cb = CircuitBreaker.of("my-circuit-breaker", CircuitBreakerConfig.custom()
      .minimumNumberOfCalls(5)
      .build());
    // end::circuit-breaker[]

    // tag::router[]
    Router router = Router.router(vertx);
    WebClient client = WebClient.create(vertx);

    router.get("/").handler(ctx -> {
      VertxCircuitBreaker.executeFuture(cb, () -> {
        return client.get(8080, "localhost", "/does-not-exist")
          .as(BodyCodec.string())
          .send()
          .expecting(HttpResponseExpectation.SC_SUCCESS);
      })
        .onSuccess(response -> ctx.end("Got: " + response.body() + "\n"))
        .onFailure(error -> ctx.end("Failed with: " + error.toString() + "\n"));
    });
    // end::router[]

    // tag::server[]
    return vertx.createHttpServer()
      .requestHandler(router)
      .listen(8080)
      .onSuccess(server -> {
        System.out.println("HTTP server started on port " + server.actualPort());
      });
    // end::server[]
  }
  // end::start[]
// tag::class[]
}
// end::class[]
