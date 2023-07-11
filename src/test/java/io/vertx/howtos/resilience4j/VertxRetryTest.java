package io.vertx.howtos.resilience4j;

import io.github.resilience4j.retry.MaxRetriesExceededException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class VertxRetryTest {
  private HelloWorldService helloWorldService;

  @BeforeEach
  public void setUp() {
    helloWorldService = mock(HelloWorldService.class);
  }

  @Test
  public void shouldNotRetry(Vertx vertx, VertxTestContext test) {
    when(helloWorldService.returnHelloWorld()).thenReturn("Hello world");
    Retry retryContext = Retry.ofDefaults("id");

    Supplier<Future<String>> supplier = VertxRetry.decorateFuture(retryContext, vertx, HelloWorldService.runAsync(vertx, helloWorldService));
    Future<String> future = supplier.get();

    future.onComplete(test.succeeding(result -> {
      assertThat(result).isEqualTo("Hello world");
      verify(helloWorldService).returnHelloWorld();
      test.completeNow();
    }));
  }

  @Test
  public void shouldNotRetryWithThatResult(Vertx vertx, VertxTestContext test) {
    when(helloWorldService.returnHelloWorld()).thenReturn("Hello world");
    RetryConfig retryConfig = RetryConfig.<String>custom()
      .retryOnResult(s -> s.contains("NoRetry"))
      .maxAttempts(1)
      .build();
    Retry retryContext = Retry.of("id", retryConfig);

    Supplier<Future<String>> supplier = VertxRetry.decorateFuture(retryContext, vertx, HelloWorldService.runAsync(vertx, helloWorldService));
    Future<String> future = supplier.get();

    future.onComplete(test.succeeding(result -> {
      assertThat(result).isEqualTo("Hello world");
      verify(helloWorldService).returnHelloWorld();
      test.completeNow();
    }));
  }

  @Test
  public void shouldRetryInCaseOfResultMatch(Vertx vertx, VertxTestContext test) {
    shouldCompleteFutureAfterAttemptsInCaseOfRetryOnAsynchronousResult(1, vertx, test);
  }

  @Test
  public void shouldRetryTowAttemptsInCaseOfResultMatch(Vertx vertx, VertxTestContext test) {
    shouldCompleteFutureAfterAttemptsInCaseOfRetryOnAsynchronousResult(2, vertx, test);
  }

  private void shouldCompleteFutureAfterAttemptsInCaseOfRetryOnAsynchronousResult(int noOfAttempts, Vertx vertx, VertxTestContext test) {
    when(helloWorldService.returnHelloWorld()).thenReturn("Hello world");
    Retry retryContext = Retry.of("id",
      RetryConfig.<String>custom()
        .maxAttempts(noOfAttempts)
        .retryOnResult(s -> s.contains("Hello world"))
        .build());

    Future<String> future = VertxRetry.executeFuture(retryContext, vertx, HelloWorldService.runAsync(vertx, helloWorldService));

    future.onComplete(test.succeeding(result -> {
      assertThat(result).isEqualTo("Hello world");
      verify(helloWorldService, times(noOfAttempts)).returnHelloWorld();
      test.completeNow();
    }));
  }

  @Test
  public void shouldRetryInCaseOfSynchronousException(Vertx vertx, VertxTestContext test) {
    when(helloWorldService.returnHelloWorld())
      .thenThrow(new HelloWorldException())
      .thenReturn("Hello world");
    Retry retry = Retry.ofDefaults("id");

    Future<String> future = VertxRetry.executeFuture(retry, vertx, HelloWorldService.runSync(helloWorldService));

    future.onComplete(test.succeeding(result -> {
      assertThat(result).isEqualTo("Hello world");
      verify(helloWorldService, times(2)).returnHelloWorld();
      test.completeNow();
    }));
  }

  @Test
  public void shouldRetryInCaseOfAsynchronousException(Vertx vertx, VertxTestContext test) {
    when(helloWorldService.returnHelloWorld())
      .thenThrow(new HelloWorldException())
      .thenReturn("Hello world");
    Retry retryContext = Retry.ofDefaults("id");

    Future<String> future = VertxRetry.executeFuture(retryContext, vertx, HelloWorldService.runAsync(vertx, helloWorldService));

    future.onComplete(test.succeeding(result -> {
      assertThat(result).isEqualTo("Hello world");
      verify(helloWorldService, times(2)).returnHelloWorld();
      test.completeNow();
    }));
  }

  @Test
  public void shouldThrowOnceMaxAttemptsReachedIfConfigured(Vertx vertx, VertxTestContext test) {
    when(helloWorldService.returnHelloWorld()).thenReturn("invalid response");
    RetryConfig retryConfig = RetryConfig.<String>custom()
      .retryOnResult(s -> s.equals("invalid response"))
      .maxAttempts(3)
      .failAfterMaxAttempts(true)
      .build();
    Retry retry = Retry.of("retry", retryConfig);

    Future<String> future = VertxRetry.executeFuture(retry, vertx, HelloWorldService.runAsync(vertx, helloWorldService));

    future.onComplete(test.failing(error -> {
      assertThat(error)
        .isInstanceOf(MaxRetriesExceededException.class)
        .hasMessage("Retry 'retry' has exhausted all attempts (3)");
      verify(helloWorldService, times(3)).returnHelloWorld();
      test.completeNow();
    }));
  }

  @Test
  public void shouldCompleteFutureAfterOneAttemptInCaseOfAsynchronousException(Vertx vertx, VertxTestContext test) {
    shouldCompleteFutureAfterAttemptsInCaseOfAsynchronousException(1, vertx, test);
  }

  @Test
  public void shouldCompleteFutureAfterTwoAttemptsInCaseOfAsynchronousException(Vertx vertx, VertxTestContext test) {
    shouldCompleteFutureAfterAttemptsInCaseOfAsynchronousException(2, vertx, test);
  }

  @Test
  public void shouldCompleteFutureAfterThreeAttemptsInCaseOfAsynchronousException(Vertx vertx, VertxTestContext test) {
    shouldCompleteFutureAfterAttemptsInCaseOfAsynchronousException(3, vertx, test);
  }

  private void shouldCompleteFutureAfterAttemptsInCaseOfAsynchronousException(int noOfAttempts, Vertx vertx, VertxTestContext test) {
    when(helloWorldService.returnHelloWorld()).thenThrow(new HelloWorldException());
    Retry retryContext = Retry.of("id", RetryConfig.custom().maxAttempts(noOfAttempts).build());
    Future<String> future = VertxRetry.executeFuture(retryContext, vertx, HelloWorldService.runAsync(vertx, helloWorldService));

    future.onComplete(test.failing(error -> {
      assertThat(error).isInstanceOf(HelloWorldException.class);
      verify(helloWorldService, times(noOfAttempts)).returnHelloWorld();
      test.completeNow();
    }));
  }
}
