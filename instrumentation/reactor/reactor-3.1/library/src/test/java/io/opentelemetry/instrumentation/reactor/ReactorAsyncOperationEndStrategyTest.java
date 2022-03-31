/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategy;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("ClassCanBeStatic")
class ReactorAsyncOperationEndStrategyTest {

  @Mock private Instrumenter<String, String> instrumenter;

  @Mock private Span span;

  private final AsyncOperationEndStrategy strategy = ReactorAsyncOperationEndStrategy.create();

  @Nested
  class MonoTest {
    @Test
    void supported() {
      assertThat(strategy.supports(Mono.class)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void endsSpanOnSuccess() {
      UnicastProcessor<String> source = UnicastProcessor.create();
      Mono<String> mono = source.singleOrEmpty();

      Mono<String> result =
          (Mono<String>) strategy.end(instrumenter, Context.root(), "request", mono, String.class);
      StepVerifier.Step<String> verifier = StepVerifier.create(result).expectSubscription();

      source.onNext("response");
      source.onComplete();
      verifier.expectNext("response").verifyComplete();

      verify(instrumenter).end(Context.root(), "request", "response", null);
    }

    @Test
    void endsSpanOnFailure() {
      IllegalStateException error = new IllegalStateException();
      UnicastProcessor<String> source = UnicastProcessor.create();
      Mono<String> mono = source.singleOrEmpty();

      Mono<?> result =
          (Mono<?>) strategy.end(instrumenter, Context.root(), "request", mono, String.class);
      StepVerifier.Step<?> verifier = StepVerifier.create(result).expectSubscription();

      source.onError(error);
      verifier.verifyErrorMatches(t -> t.equals(error));

      verify(instrumenter).end(Context.root(), "request", null, error);
    }

    @Test
    void endsSpanOnEmpty() {
      UnicastProcessor<String> source = UnicastProcessor.create();
      Mono<String> mono = source.singleOrEmpty();

      Mono<?> result =
          (Mono<?>) strategy.end(instrumenter, Context.root(), "request", mono, String.class);
      StepVerifier.Step<?> verifier = StepVerifier.create(result).expectSubscription();

      source.onComplete();
      verifier.verifyComplete();

      verify(instrumenter).end(Context.root(), "request", null, null);
    }

    @Test
    void endsSpanOnCancel() {
      when(span.storeInContext(any())).thenCallRealMethod();
      UnicastProcessor<String> source = UnicastProcessor.create();
      Mono<String> mono = source.singleOrEmpty();
      Context context = Context.root().with(span);

      Mono<?> result = (Mono<?>) strategy.end(instrumenter, context, "request", mono, String.class);
      StepVerifier.Step<?> verifier = StepVerifier.create(result).expectSubscription();

      verifier.thenCancel().verify();

      verify(instrumenter).end(context, "request", null, null);
    }

    @Test
    void endsSpanOnCancelExperimentalAttribute() {
      when(span.storeInContext(any())).thenCallRealMethod();
      when(span.setAttribute(ReactorAsyncOperationEndStrategy.CANCELED_ATTRIBUTE_KEY, true))
          .thenReturn(span);

      AsyncOperationEndStrategy strategy =
          ReactorAsyncOperationEndStrategy.builder()
              .setCaptureExperimentalSpanAttributes(true)
              .build();

      UnicastProcessor<String> source = UnicastProcessor.create();
      Mono<String> mono = source.singleOrEmpty();
      Context context = Context.root().with(span);

      Mono<?> result = (Mono<?>) strategy.end(instrumenter, context, "request", mono, String.class);
      StepVerifier.Step<?> verifier = StepVerifier.create(result).expectSubscription();

      verifier.thenCancel().verify();

      verify(instrumenter).end(context, "request", null, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void endsSpanOnImmediateSuccess() {
      Mono<String> result =
          (Mono<String>)
              strategy.end(
                  instrumenter, Context.root(), "request", Mono.just("response"), String.class);
      StepVerifier.create(result).expectNext("response").verifyComplete();

      verify(instrumenter).end(Context.root(), "request", "response", null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void endsSpanOnImmediateFailure() {
      IllegalStateException error = new IllegalStateException();
      Mono<String> result =
          (Mono<String>)
              strategy.end(
                  instrumenter, Context.root(), "request", Mono.error(error), String.class);
      StepVerifier.create(result).verifyErrorMatches(t -> t.equals(error));

      verify(instrumenter).end(Context.root(), "request", null, error);
    }

    @Test
    @SuppressWarnings("unchecked")
    void endsSpanOnImmediateEmpty() {
      Mono<String> result =
          (Mono<String>)
              strategy.end(instrumenter, Context.root(), "request", Mono.empty(), String.class);
      StepVerifier.create(result).verifyComplete();

      verify(instrumenter).end(Context.root(), "request", null, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void endsSpanOnceForMultipleSubscribers() {
      Mono<String> result =
          (Mono<String>)
              strategy.end(
                  instrumenter, Context.root(), "request", Mono.just("response"), String.class);
      StepVerifier.create(result).expectNext("response").verifyComplete();
      StepVerifier.create(result).expectNext("response").verifyComplete();
      StepVerifier.create(result).expectNext("response").verifyComplete();

      verify(instrumenter).end(Context.root(), "request", "response", null);
    }
  }

  @Nested
  class FluxTest {
    @Test
    void supported() {
      assertThat(strategy.supports(Flux.class)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void endsSpanOnSuccess() {
      UnicastProcessor<String> source = UnicastProcessor.create();

      Flux<String> result =
          (Flux<String>)
              strategy.end(instrumenter, Context.root(), "request", source, String.class);
      StepVerifier.Step<String> verifier = StepVerifier.create(result).expectSubscription();

      source.onNext("response");
      source.onComplete();
      verifier.expectNext("response").verifyComplete();

      // For a flux we do not capture a response since there are multiple.
      verify(instrumenter).end(Context.root(), "request", null, null);
    }

    @Test
    void endsSpanOnFailure() {
      IllegalStateException error = new IllegalStateException();
      UnicastProcessor<String> source = UnicastProcessor.create();

      Flux<?> result =
          (Flux<?>) strategy.end(instrumenter, Context.root(), "request", source, String.class);
      StepVerifier.Step<?> verifier = StepVerifier.create(result).expectSubscription();

      source.onError(error);
      verifier.verifyErrorMatches(t -> t.equals(error));

      verify(instrumenter).end(Context.root(), "request", null, error);
    }

    @Test
    void endsSpanOnEmpty() {
      UnicastProcessor<String> source = UnicastProcessor.create();

      Flux<?> result =
          (Flux<?>) strategy.end(instrumenter, Context.root(), "request", source, String.class);
      StepVerifier.Step<?> verifier = StepVerifier.create(result).expectSubscription();

      source.onComplete();
      verifier.verifyComplete();

      verify(instrumenter).end(Context.root(), "request", null, null);
    }

    @Test
    void endsSpanOnCancel() {
      when(span.storeInContext(any())).thenCallRealMethod();
      UnicastProcessor<String> source = UnicastProcessor.create();
      Context context = Context.root().with(span);

      Flux<?> result =
          (Flux<?>) strategy.end(instrumenter, context, "request", source, String.class);
      StepVerifier.Step<?> verifier = StepVerifier.create(result).expectSubscription();

      verifier.thenCancel().verify();

      verify(instrumenter).end(context, "request", null, null);
    }

    @Test
    void endsSpanOnCancelExperimentalAttribute() {
      when(span.storeInContext(any())).thenCallRealMethod();
      when(span.setAttribute(ReactorAsyncOperationEndStrategy.CANCELED_ATTRIBUTE_KEY, true))
          .thenReturn(span);

      AsyncOperationEndStrategy strategy =
          ReactorAsyncOperationEndStrategy.builder()
              .setCaptureExperimentalSpanAttributes(true)
              .build();

      UnicastProcessor<String> source = UnicastProcessor.create();
      Context context = Context.root().with(span);

      Flux<?> result =
          (Flux<?>) strategy.end(instrumenter, context, "request", source, String.class);
      StepVerifier.Step<?> verifier = StepVerifier.create(result).expectSubscription();

      verifier.thenCancel().verify();

      verify(instrumenter).end(context, "request", null, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void endsSpanOnImmediateSuccess() {
      Flux<String> result =
          (Flux<String>)
              strategy.end(
                  instrumenter, Context.root(), "request", Flux.just("response"), String.class);
      StepVerifier.create(result).expectNext("response").verifyComplete();

      verify(instrumenter).end(Context.root(), "request", null, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void endsSpanOnImmediateFailure() {
      IllegalStateException error = new IllegalStateException();
      Flux<String> result =
          (Flux<String>)
              strategy.end(
                  instrumenter, Context.root(), "request", Flux.error(error), String.class);
      StepVerifier.create(result).verifyErrorMatches(t -> t.equals(error));

      verify(instrumenter).end(Context.root(), "request", null, error);
    }

    @Test
    @SuppressWarnings("unchecked")
    void endsSpanOnImmediateEmpty() {
      Flux<String> result =
          (Flux<String>)
              strategy.end(instrumenter, Context.root(), "request", Flux.empty(), String.class);
      StepVerifier.create(result).verifyComplete();

      verify(instrumenter).end(Context.root(), "request", null, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void endsSpanOnceForMultipleSubscribers() {
      Flux<String> result =
          (Flux<String>)
              strategy.end(
                  instrumenter, Context.root(), "request", Flux.just("response"), String.class);
      StepVerifier.create(result).expectNext("response").verifyComplete();
      StepVerifier.create(result).expectNext("response").verifyComplete();
      StepVerifier.create(result).expectNext("response").verifyComplete();

      verify(instrumenter).end(Context.root(), "request", null, null);
    }
  }
}
