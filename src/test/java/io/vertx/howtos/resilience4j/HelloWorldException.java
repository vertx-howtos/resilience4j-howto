package io.vertx.howtos.resilience4j;

public class HelloWorldException extends RuntimeException {
  public HelloWorldException() {
    super();
  }

  public HelloWorldException(String message) {
    super(message);
  }
}
