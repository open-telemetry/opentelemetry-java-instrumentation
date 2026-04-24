/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor.v3_1;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class HooksTest {

  private static final Span PARENT_SPAN =
      Span.wrap(
          SpanContext.create(
              "11111111111111111111111111111111",
              "1111111111111111",
              TraceFlags.getSampled(),
              TraceState.getDefault()));

  @AfterEach
  void resetHooks() {
    Hooks.resetOnEachOperator(TracingSubscriber.class.getName());
    if (schedulerHooksSupported()) {
      Schedulers.resetOnScheduleHook(RunnableWrapper.class.getName());
    }
  }

  @Test
  void canResetOurHooks() {
    ContextPropagationOperator operator = ContextPropagationOperator.create();
    AtomicReference<CoreSubscriber<? super Integer>> subscriber = new AtomicReference<>();

    new CapturingMono(subscriber).map(i -> i + 1).subscribe();
    assertThat(subscriber.get()).extracting("actual").isNotInstanceOf(TracingSubscriber.class);

    operator.registerOnEachOperator();
    new CapturingMono(subscriber).map(i -> i + 1).subscribe();
    assertThat(subscriber.get()).extracting("actual").isInstanceOf(TracingSubscriber.class);

    operator.resetOnEachOperator();
    new CapturingMono(subscriber).map(i -> i + 1).subscribe();
    assertThat(subscriber.get()).extracting("actual").isNotInstanceOf(TracingSubscriber.class);
  }

  @Test
  void canResetSchedulerHook() throws InterruptedException {
    assumeTrue(schedulerHooksSupported());

    ContextPropagationOperator operator = ContextPropagationOperator.create();

    assertThat(schedulerPropagatesContext()).isFalse();

    operator.registerOnEachOperator();
    assertThat(schedulerPropagatesContext()).isTrue();

    operator.resetOnEachOperator();
    assertThat(schedulerPropagatesContext()).isFalse();
  }

  @Test
  void testInvalidBlockUsage() throws InterruptedException {
    ContextPropagationOperator operator = ContextPropagationOperator.create();
    operator.registerOnEachOperator();

    Callable<String> callable =
        () -> {
          Mono.just("test1").block();
          return "call1";
        };

    Disposable disposable =
        Mono.defer(
                () ->
                    Mono.fromCallable(callable).publishOn(Schedulers.single()).flatMap(Mono::just))
            .subscribeOn(Schedulers.single())
            .subscribe();

    MILLISECONDS.sleep(100);

    disposable.dispose();
    operator.resetOnEachOperator();
  }

  private static boolean schedulerHooksSupported() {
    try {
      Schedulers.class.getMethod("onScheduleHook", String.class, Function.class);
      Schedulers.class.getMethod("resetOnScheduleHook", String.class);
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  private static boolean schedulerPropagatesContext() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean currentSpanValid = new AtomicBoolean(false);
    try (Scope ignored = Context.root().with(PARENT_SPAN).makeCurrent()) {
      Schedulers.single()
          .schedule(
              () -> {
                currentSpanValid.set(Span.current().getSpanContext().isValid());
                latch.countDown();
              });
    }
    assertThat(latch.await(5, SECONDS)).isTrue();
    return currentSpanValid.get();
  }

  private static class CapturingMono extends Mono<Integer> {
    private final AtomicReference<CoreSubscriber<? super Integer>> subscriber;

    private CapturingMono(AtomicReference<CoreSubscriber<? super Integer>> subscriber) {
      this.subscriber = subscriber;
    }

    @Override
    public void subscribe(CoreSubscriber<? super Integer> actual) {
      subscriber.set(actual);
    }
  }
}
