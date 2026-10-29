/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor.v3_1;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class ContextBoundaryTest {
  private static final ContextKey<String> KEY = ContextKey.named("boundary-test");
  private static final Context OPERATION = Context.root().with(KEY, "operation");
  private static final Context APPLICATION = Context.root().with(KEY, "application");
  private static final Context UNRELATED = Context.root().with(KEY, "unrelated");
  private static final Context INNER = Context.root().with(KEY, "inner");
  private static final MethodHandle MONO_CONTEXT_WRITE_METHOD = getMonoContextWriteMethod();

  private final ContextPropagationOperator operator = ContextPropagationOperator.create();

  @AfterEach
  void resetHooks() {
    operator.resetOnEachOperator();
  }

  private static Stream<Arguments> boundaries() {
    return Stream.of(false, true)
        .flatMap(
            mono ->
                Stream.of(false, true)
                    .flatMap(
                        hooks ->
                            Stream.of(Context.root(), APPLICATION)
                                .flatMap(
                                    application ->
                                        Stream.of(Context.root(), OPERATION)
                                            .map(
                                                operation ->
                                                    Arguments.of(
                                                        mono, hooks, operation, application)))));
  }

  private static Stream<Arguments> representativeBoundaries() {
    return Stream.of(
        Arguments.of(true, false, OPERATION, Context.root()),
        Arguments.of(false, false, Context.root(), APPLICATION),
        Arguments.of(true, true, Context.root(), APPLICATION),
        Arguments.of(false, true, OPERATION, Context.root()));
  }

  private static Stream<Arguments> directSubscriberBoundaries() {
    return Stream.of(false, true)
        .flatMap(
            mono ->
                Stream.of(false, true)
                    .flatMap(
                        rootApplication ->
                            Stream.of(false, true)
                                .map(wrapped -> Arguments.of(mono, rootApplication, wrapped))));
  }

  private static Stream<Arguments> nestedPublisherBoundaries() {
    return Stream.of(false, true)
        .flatMap(mono -> Stream.of(Context.root(), INNER).map(inner -> Arguments.of(mono, inner)));
  }

  private static Stream<Arguments> terminalCallbacks() {
    return representativeBoundaries()
        .flatMap(
            boundary ->
                Stream.of(false, true)
                    .map(
                        error -> {
                          Object[] args = boundary.get();
                          return Arguments.of(args[0], args[1], args[2], args[3], error);
                        }));
  }

  @ParameterizedTest
  @MethodSource("boundaries")
  void scopesSubscriptionDemandAndSignals(
      boolean mono, boolean hooks, Context operation, Context application) throws Exception {
    if (hooks) {
      operator.registerOnEachOperator();
    }
    Probe probe = new Probe(operation);
    probe.observeSourceSignals = true;
    RecordingSubscriber actual = new RecordingSubscriber(application);
    try (Scope ignored = UNRELATED.makeCurrent()) {
      subscribe(mono, probe, operation, application, actual);
      assertThat(Context.current()).isSameAs(UNRELATED);
      actual.subscription.request(2);
      assertThat(Context.current()).isSameAs(UNRELATED);
    }
    assertThat(probe.requests).containsExactly(2L);
    assertThat(actual.contexts).containsExactly(application);
    assertThat(actual.currentContext().get("unrelated").toString()).isEqualTo("preserved");
    assertThat(ContextPropagationOperator.getOpenTelemetryContext(actual.currentContext(), null))
        .isSameAs(Context.root());

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      executor
          .submit(
              () -> {
                try (Scope ignored = UNRELATED.makeCurrent()) {
                  probe.actual.onNext("value");
                  assertThat(Context.current()).isSameAs(UNRELATED);
                  probe.actual.onComplete();
                  assertThat(Context.current()).isSameAs(UNRELATED);
                }
                assertThat(Context.current()).isSameAs(Context.root());
              })
          .get(10, SECONDS);
    } finally {
      executor.shutdownNow();
    }
    assertThat(actual.values).containsExactly("value");
    assertThat(actual.contexts).containsExactly(application, application, application);
    assertThat(actual.complete).isTrue();
    assertThat(probe.sourceSignalContexts).containsExactly(hooks ? operation : UNRELATED);
  }

  @ParameterizedTest
  @MethodSource("nestedPublisherBoundaries")
  void keepsExplicitContextForIndependentPublisher(boolean mono, Context inner) {
    operator.registerOnEachOperator();
    AtomicReference<Context> observed = new AtomicReference<>();
    try (Scope ignored = Context.root().makeCurrent()) {
      if (mono) {
        ContextPropagationOperator.runWithContext(Mono.just("value"), OPERATION, APPLICATION)
            .map(value -> value)
            .doOnNext(
                value -> {
                  withStoredContext(
                          Mono.just(value)
                              .map(
                                  innerValue -> {
                                    assertThat(Context.current()).isSameAs(inner);
                                    return innerValue;
                                  }),
                          inner)
                      .block();
                  assertThat(Context.current()).isSameAs(APPLICATION);
                  observed.set(Context.current());
                })
            .block();
      } else {
        ContextPropagationOperator.runWithContext(Flux.just("value"), OPERATION, APPLICATION)
            .map(value -> value)
            .doOnNext(
                value -> {
                  withStoredContext(
                          Mono.just(value)
                              .map(
                                  innerValue -> {
                                    assertThat(Context.current()).isSameAs(inner);
                                    return innerValue;
                                  }),
                          inner)
                      .block();
                  assertThat(Context.current()).isSameAs(APPLICATION);
                  observed.set(Context.current());
                })
            .collectList()
            .block();
      }
      assertThat(Context.current()).isSameAs(Context.root());
    }
    assertThat(observed.get()).isSameAs(APPLICATION);
  }

  @ParameterizedTest
  @MethodSource("directSubscriberBoundaries")
  void directlyConstructedTracingSubscribersKeepTheirCapturedContext(
      boolean mono, boolean rootApplication, boolean wrapped) {
    Context application = rootApplication ? Context.root() : APPLICATION;
    RecordingSubscriber recording = new RecordingSubscriber(UNRELATED);
    TracingSubscriber<String> subscriber;
    try (Scope ignored = UNRELATED.makeCurrent()) {
      subscriber = new TracingSubscriber<>(recording, reactor.util.context.Context.empty());
      if (wrapped) {
        subscriber = new TracingSubscriber<>(subscriber, reactor.util.context.Context.empty());
      }

      if (mono) {
        ContextPropagationOperator.runWithContext(Mono.just("value"), OPERATION, application)
            .subscribe(subscriber);
      } else {
        ContextPropagationOperator.runWithContext(Flux.just("value"), OPERATION, application)
            .subscribe(subscriber);
      }
    }
    recording.subscription.request(1);

    assertThat(recording.contexts).containsExactly(UNRELATED, UNRELATED, UNRELATED);
    assertThat(recording.values).containsExactly("value");
    assertThat(recording.complete).isTrue();
  }

  @ParameterizedTest
  @MethodSource("boundaries")
  void scopesDelayedCancellation(
      boolean mono, boolean hooks, Context operation, Context application) {
    if (hooks) {
      operator.registerOnEachOperator();
    }
    Probe probe = new Probe(operation);
    RecordingSubscriber actual = new RecordingSubscriber(application);
    subscribe(mono, probe, operation, application, actual);
    try (Scope ignored = UNRELATED.makeCurrent()) {
      actual.subscription.cancel();
      assertThat(Context.current()).isSameAs(UNRELATED);
    }
    assertThat(probe.cancelled).isTrue();
    assertThat(actual.contexts).containsExactly(application);
  }

  @ParameterizedTest
  @MethodSource("boundaries")
  void scopesUpstreamContextLookup(
      boolean mono, boolean hooks, Context operation, Context application) {
    if (hooks) {
      operator.registerOnEachOperator();
    }
    RecordingSubscriber actual =
        new RecordingSubscriber(application) {
          @Override
          public reactor.util.context.Context currentContext() {
            assertThat(Context.current()).isSameAs(operation);
            return super.currentContext();
          }
        };
    Probe probe = new Probe(operation);
    subscribe(mono, probe, operation, application, actual);
    try (Scope ignored = UNRELATED.makeCurrent()) {
      assertThat(
              ContextPropagationOperator.getOpenTelemetryContext(
                  probe.actual.currentContext(), null))
          .isSameAs(operation);
      assertThat(Context.current()).isSameAs(UNRELATED);
    }
  }

  @ParameterizedTest
  @MethodSource("boundaries")
  void scopesErrorsAndRestoresEmitterContext(
      boolean mono, boolean hooks, Context operation, Context application) {
    if (hooks) {
      operator.registerOnEachOperator();
    }
    Probe probe = new Probe(operation);
    RecordingSubscriber actual = new RecordingSubscriber(application);
    IllegalStateException error = new IllegalStateException("source");
    try (Scope ignored = UNRELATED.makeCurrent()) {
      subscribe(mono, probe, operation, application, actual);
      probe.actual.onError(error);
      assertThat(Context.current()).isSameAs(UNRELATED);
    }
    assertThat(actual.error).isSameAs(error);
    assertThat(actual.contexts).containsExactly(application, application);
  }

  @ParameterizedTest
  @MethodSource("representativeBoundaries")
  void restoresScopesAfterThrowingRequestAndCancel(
      boolean mono, boolean hooks, Context operation, Context application) {
    if (hooks) {
      operator.registerOnEachOperator();
    }
    Probe probe = new Probe(operation);
    probe.throwOnDemand = true;
    RecordingSubscriber actual = new RecordingSubscriber(application);
    try (Scope ignored = UNRELATED.makeCurrent()) {
      subscribe(mono, probe, operation, application, actual);
      assertThatThrownBy(() -> actual.subscription.request(1)).isSameAs(probe.failure);
      assertThat(Context.current()).isSameAs(UNRELATED);
      assertThatThrownBy(() -> actual.subscription.cancel()).isSameAs(probe.failure);
      assertThat(Context.current()).isSameAs(UNRELATED);
    }
  }

  @ParameterizedTest
  @MethodSource("boundaries")
  void supportsReentrantDemand(
      boolean mono, boolean hooks, Context operation, Context application) {
    if (hooks) {
      operator.registerOnEachOperator();
    }
    Probe probe = new Probe(operation);
    RecordingSubscriber actual =
        new RecordingSubscriber(application) {
          @Override
          public void onSubscribe(Subscription subscription) {
            super.onSubscribe(subscription);
            subscription.request(1);
            assertThat(Context.current()).isSameAs(application);
            subscription.cancel();
            assertThat(Context.current()).isSameAs(application);
          }
        };
    try (Scope ignored = UNRELATED.makeCurrent()) {
      subscribe(mono, probe, operation, application, actual);
      assertThat(Context.current()).isSameAs(UNRELATED);
    }
    assertThat(probe.requests).containsExactly(1L);
    assertThat(probe.cancelled).isTrue();
  }

  @ParameterizedTest
  @MethodSource("representativeBoundaries")
  void restoresScopesWhenDownstreamThrows(
      boolean mono, boolean hooks, Context operation, Context application) {
    if (hooks) {
      operator.registerOnEachOperator();
    }
    IllegalStateException failure = new IllegalStateException("downstream");
    RecordingSubscriber actual =
        new RecordingSubscriber(application) {
          @Override
          public void onNext(String value) {
            assertThat(Context.current()).isSameAs(application);
            throw failure;
          }
        };
    Probe probe = new Probe(operation);
    subscribe(mono, probe, operation, application, actual);
    try (Scope ignored = UNRELATED.makeCurrent()) {
      assertThatThrownBy(() -> probe.actual.onNext("value")).isSameAs(failure);
      assertThat(Context.current()).isSameAs(UNRELATED);
    }
  }

  @ParameterizedTest
  @MethodSource("representativeBoundaries")
  void restoresScopesWhenSubscriptionThrows(
      boolean mono, boolean hooks, Context operation, Context application) {
    if (hooks) {
      operator.registerOnEachOperator();
    }
    IllegalStateException failure = new IllegalStateException("onSubscribe");
    RecordingSubscriber actual =
        new RecordingSubscriber(application) {
          @Override
          public void onSubscribe(Subscription subscription) {
            assertThat(Context.current()).isSameAs(application);
            throw failure;
          }
        };
    try (Scope ignored = UNRELATED.makeCurrent()) {
      assertThatThrownBy(
              () -> subscribe(mono, new Probe(operation), operation, application, actual))
          .isSameAs(failure);
      assertThat(Context.current()).isSameAs(UNRELATED);
    }
  }

  @ParameterizedTest
  @MethodSource("terminalCallbacks")
  void restoresScopesWhenTerminalCallbackThrows(
      boolean mono, boolean hooks, Context operation, Context application, boolean error) {
    if (hooks) {
      operator.registerOnEachOperator();
    }
    IllegalStateException failure = new IllegalStateException("terminal");
    RecordingSubscriber actual =
        new RecordingSubscriber(application) {
          @Override
          public void onError(Throwable error) {
            assertThat(Context.current()).isSameAs(application);
            throw failure;
          }

          @Override
          public void onComplete() {
            assertThat(Context.current()).isSameAs(application);
            throw failure;
          }
        };
    Probe probe = new Probe(operation);
    subscribe(mono, probe, operation, application, actual);
    try (Scope ignored = UNRELATED.makeCurrent()) {
      assertThatThrownBy(
              () -> {
                if (error) {
                  probe.actual.onError(new IllegalStateException("source"));
                } else {
                  probe.actual.onComplete();
                }
              })
          .isSameAs(failure);
      assertThat(Context.current()).isSameAs(UNRELATED);
    }
  }

  @ParameterizedTest
  @MethodSource("representativeBoundaries")
  void supportsMultipleSubscriptions(
      boolean mono, boolean hooks, Context operation, Context application) {
    if (hooks) {
      operator.registerOnEachOperator();
    }
    AtomicInteger subscriptions = new AtomicInteger();
    Mono<String> monoSource =
        Mono.fromCallable(
            () -> {
              assertThat(Context.current()).isSameAs(operation);
              return "subscription-" + subscriptions.incrementAndGet();
            });
    RecordingSubscriber first = new RecordingSubscriber(application);
    RecordingSubscriber second = new RecordingSubscriber(application);
    try (Scope ignored = UNRELATED.makeCurrent()) {
      if (mono) {
        Mono<String> boundary =
            ContextPropagationOperator.runWithContext(monoSource, operation, application);
        boundary.subscribe(first);
        boundary.subscribe(second);
      } else {
        Flux<String> boundary =
            ContextPropagationOperator.runWithContext(monoSource.flux(), operation, application);
        boundary.subscribe(first);
        boundary.subscribe(second);
      }
      first.subscription.request(1);
      second.subscription.request(1);
      assertThat(Context.current()).isSameAs(UNRELATED);
    }
    assertThat(first.values).containsExactly("subscription-1");
    assertThat(second.values).containsExactly("subscription-2");
    assertThat(first.complete).isTrue();
    assertThat(second.complete).isTrue();
  }

  @ParameterizedTest
  @MethodSource("representativeBoundaries")
  void scheduledUpstreamUsesRegisteredHooks(
      boolean mono, boolean hooks, Context operation, Context application) {
    assumeTrue(hooks && schedulerHooksSupported());
    operator.registerOnEachOperator();
    AtomicInteger calls = new AtomicInteger();
    Mono<String> source =
        Mono.fromCallable(
                () -> {
                  assertThat(Context.current()).isSameAs(operation);
                  calls.incrementAndGet();
                  return "scheduled";
                })
            .subscribeOn(Schedulers.single());
    try (Scope ignored = application.makeCurrent()) {
      if (mono) {
        assertThat(
                ContextPropagationOperator.runWithContext(source, operation, application)
                    .doOnNext(value -> assertThat(Context.current()).isSameAs(application))
                    .block())
            .isEqualTo("scheduled");
      } else {
        assertThat(
                ContextPropagationOperator.runWithContext(source.flux(), operation, application)
                    .doOnNext(value -> assertThat(Context.current()).isSameAs(application))
                    .collectList()
                    .block())
            .containsExactly("scheduled");
      }
      assertThat(Context.current()).isSameAs(application);
    }
    assertThat(calls).hasValue(1);
  }

  private static boolean schedulerHooksSupported() {
    try {
      Schedulers.class.getMethod("onScheduleHook", String.class, Function.class);
      return true;
    } catch (NoSuchMethodException ignored) {
      return false;
    }
  }

  private static MethodHandle getMonoContextWriteMethod() {
    MethodHandles.Lookup lookup = MethodHandles.publicLookup();
    try {
      return lookup.findVirtual(
          Mono.class, "contextWrite", MethodType.methodType(Mono.class, Function.class));
    } catch (NoSuchMethodException | IllegalAccessException ignored) {
      // ignore
    }
    try {
      return lookup.findVirtual(
          Mono.class, "subscriberContext", MethodType.methodType(Mono.class, Function.class));
    } catch (NoSuchMethodException | IllegalAccessException ignored) {
      return null;
    }
  }

  @ParameterizedTest
  @MethodSource("representativeBoundaries")
  void scalarEmptyErrorAndRetry(
      boolean mono, boolean hooks, Context operation, Context application) {
    if (hooks) {
      operator.registerOnEachOperator();
    }
    AtomicInteger attempts = new AtomicInteger();
    try (Scope ignored = application.makeCurrent()) {
      if (mono) {
        assertThat(
                ContextPropagationOperator.runWithContext(
                        Mono.just("scalar"), operation, application)
                    .doOnNext(value -> assertThat(Context.current()).isSameAs(application))
                    .block())
            .isEqualTo("scalar");
        assertThat(
                ContextPropagationOperator.runWithContext(Mono.empty(), operation, application)
                    .doOnSuccess(value -> assertThat(Context.current()).isSameAs(application))
                    .block())
            .isNull();
        assertThat(
                ContextPropagationOperator.runWithContext(
                        Mono.fromCallable(
                            () -> {
                              assertThat(Context.current()).isSameAs(operation);
                              if (attempts.getAndIncrement() == 0) {
                                throw new IllegalStateException("retry");
                              }
                              return "retried";
                            }),
                        operation,
                        application)
                    .doOnError(error -> assertThat(Context.current()).isSameAs(application))
                    .retry(1)
                    .block())
            .isEqualTo("retried");
      } else {
        assertThat(
                ContextPropagationOperator.runWithContext(
                        Flux.just("first", "second"), operation, application)
                    .doOnNext(value -> assertThat(Context.current()).isSameAs(application))
                    .collectList()
                    .block())
            .containsExactly("first", "second");
        assertThat(
                ContextPropagationOperator.runWithContext(Flux.empty(), operation, application)
                    .doOnComplete(() -> assertThat(Context.current()).isSameAs(application))
                    .collectList()
                    .block())
            .isEmpty();
        assertThat(
                ContextPropagationOperator.runWithContext(
                        Flux.defer(
                            () -> {
                              assertThat(Context.current()).isSameAs(operation);
                              return attempts.getAndIncrement() == 0
                                  ? Flux.error(new IllegalStateException("retry"))
                                  : Flux.just("retried");
                            }),
                        operation,
                        application)
                    .doOnError(error -> assertThat(Context.current()).isSameAs(application))
                    .retry(1)
                    .collectList()
                    .block())
            .containsExactly("retried");
      }
      assertThat(Context.current()).isSameAs(application);
    }
    assertThat(attempts).hasValue(2);
  }

  private static void subscribe(
      boolean mono,
      Probe probe,
      Context operation,
      Context application,
      RecordingSubscriber actual) {
    if (mono) {
      Mono<String> source =
          new Mono<String>() {
            @Override
            public void subscribe(CoreSubscriber<? super String> subscriber) {
              probe.subscribe(subscriber);
            }
          };
      if (probe.observeSourceSignals) {
        source =
            source
                .map(value -> value)
                .doOnNext(value -> probe.sourceSignalContexts.add(Context.current()));
      }
      Mono<String> boundary =
          ContextPropagationOperator.runWithContext(source, operation, application);
      assertThat(boundary).isNotInstanceOf(Fuseable.class);
      boundary.subscribe(actual);
    } else {
      Flux<String> source =
          new Flux<String>() {
            @Override
            public void subscribe(CoreSubscriber<? super String> subscriber) {
              probe.subscribe(subscriber);
            }
          };
      if (probe.observeSourceSignals) {
        source =
            source
                .map(value -> value)
                .doOnNext(value -> probe.sourceSignalContexts.add(Context.current()));
      }
      Flux<String> boundary =
          ContextPropagationOperator.runWithContext(source, operation, application);
      assertThat(boundary).isNotInstanceOf(Fuseable.class);
      boundary.subscribe(actual);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> Mono<T> withStoredContext(Mono<T> source, Context context) {
    try {
      return (Mono<T>)
          MONO_CONTEXT_WRITE_METHOD.invoke(
              source,
              (Function<reactor.util.context.Context, reactor.util.context.Context>)
                  reactorContext ->
                      ContextPropagationOperator.storeOpenTelemetryContext(
                          reactorContext, context));
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  private static final class Probe {
    private final Context operation;
    private final List<Long> requests = new ArrayList<>();
    private final List<Context> sourceSignalContexts = new ArrayList<>();
    private final IllegalStateException failure = new IllegalStateException("demand");
    private CoreSubscriber<? super String> actual;
    private boolean cancelled;
    private boolean throwOnDemand;
    private boolean observeSourceSignals;

    private Probe(Context operation) {
      this.operation = operation;
    }

    private void subscribe(CoreSubscriber<? super String> actual) {
      assertThat(Context.current()).isSameAs(operation);
      assertThat(actual.currentContext().get("unrelated").toString()).isEqualTo("preserved");
      assertThat(ContextPropagationOperator.getOpenTelemetryContext(actual.currentContext(), null))
          .isSameAs(operation);
      this.actual = actual;
      actual.onSubscribe(
          new Subscription() {
            @Override
            public void request(long count) {
              assertThat(Context.current()).isSameAs(operation);
              requests.add(count);
              if (throwOnDemand) {
                throw failure;
              }
            }

            @Override
            public void cancel() {
              assertThat(Context.current()).isSameAs(operation);
              cancelled = true;
              if (throwOnDemand) {
                throw failure;
              }
            }
          });
      assertThat(Context.current()).isSameAs(operation);
    }
  }

  private static class RecordingSubscriber implements CoreSubscriber<String> {
    private final Context application;
    private final List<Context> contexts = new ArrayList<>();
    private final List<String> values = new ArrayList<>();
    private Subscription subscription;
    private Throwable error;
    private boolean complete;

    private RecordingSubscriber(Context application) {
      this.application = application;
    }

    @Override
    public reactor.util.context.Context currentContext() {
      return ContextPropagationOperator.storeOpenTelemetryContext(
          reactor.util.context.Context.of("unrelated", "preserved"), Context.root());
    }

    @Override
    public void onSubscribe(Subscription subscription) {
      recordContext();
      this.subscription = subscription;
    }

    @Override
    public void onNext(String value) {
      recordContext();
      values.add(value);
    }

    @Override
    public void onError(Throwable error) {
      recordContext();
      this.error = error;
    }

    @Override
    public void onComplete() {
      recordContext();
      complete = true;
    }

    private void recordContext() {
      assertThat(Context.current()).isSameAs(application);
      contexts.add(Context.current());
    }
  }
}
