/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.v3_1;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.lang.invoke.MethodType.methodType;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.reactor.v3_1.TracingSubscriber;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.test.StepVerifier;

class ContextPropagationOperatorInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final MethodHandle MONO_CONTEXT_WRITE_METHOD = getContextWriteMethod(Mono.class);
  private static final ContextKey<String> APPLICATION_KEY = ContextKey.named("application-value");

  private static MethodHandle getContextWriteMethod(Class<?> type) {
    MethodHandles.Lookup lookup = MethodHandles.publicLookup();
    try {
      return lookup.findVirtual(type, "contextWrite", methodType(type, Function.class));
    } catch (NoSuchMethodException | IllegalAccessException ignored) {
      // ignore
    }
    try {
      return lookup.findVirtual(type, "subscriberContext", methodType(type, Function.class));
    } catch (NoSuchMethodException | IllegalAccessException ignored) {
      // ignore
    }
    return null;
  }

  @Test
  void storeAndGetContext() {
    reactor.util.context.Context reactorContext = reactor.util.context.Context.empty();
    testing.runWithSpan(
        "parent",
        () -> {
          reactor.util.context.Context newReactorContext =
              ContextPropagationOperator.storeOpenTelemetryContext(
                  reactorContext, Context.current());
          Context otelContext =
              ContextPropagationOperator.getOpenTelemetryContext(newReactorContext, null);
          assertThat(otelContext).isNotNull();
          Span.fromContext(otelContext).setAttribute("foo", "bar");
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(equalTo(stringKey("foo"), "bar"))));
  }

  @Test
  void missingContext() {
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(
                  ContextPropagationOperator.getOpenTelemetryContext(
                      reactor.util.context.Context.empty(), null))
              .isNull();
          Context otelContext =
              ContextPropagationOperator.getOpenTelemetryContext(
                  reactor.util.context.Context.empty(), Context.current());
          Span.fromContext(otelContext).setAttribute("foo", "bar");
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(equalTo(stringKey("foo"), "bar"))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void withoutSubscriptionContextCaptureDoesNotCaptureAgentContext(boolean flux) {
    reactor.util.context.Context reactorContext =
        ContextPropagationOperator.withoutSubscriptionContextCapture(
            reactor.util.context.Context.of("retained", "value"));
    assertThat(reactorContext.<String>get("retained")).isEqualTo("value");
    Context fallback = Context.root().with(APPLICATION_KEY, "fallback");
    assertThat(ContextPropagationOperator.getOpenTelemetryContext(reactorContext, fallback))
        .isSameAs(fallback);

    testing.runWithSpan(
        "ambient",
        () -> {
          AgentContextState agentState = new AgentContextState();
          try (Scope ignored = Context.current().with(APPLICATION_KEY, "ambient").makeCurrent();
              Scope agentScope = agentState.makeCurrent()) {
            AtomicReference<MonoSink<String>> sink = new AtomicReference<>();
            Mono<String> source =
                Mono.<String>create(sink::set)
                    .flatMap(
                        value -> {
                          assertThat(Span.current().getSpanContext().isValid()).isTrue();
                          assertThat(Context.current().get(APPLICATION_KEY)).isEqualTo("signal");
                          agentState.assertPresent(false);
                          return new ExtensionAnnotationsTracedWithSpan().mono(Mono.just(value));
                        });
            Runnable emit =
                () -> {
                  try (Scope root = Context.root().makeCurrent()) {
                    testing.runWithSpan(
                        "signal",
                        () -> {
                          try (Scope signal =
                              Context.current().with(APPLICATION_KEY, "signal").makeCurrent()) {
                            sink.get().success("value");
                          }
                        });
                  }
                };
            if (flux) {
              Flux<String> result =
                  Flux.from(source).transform(publisher -> writeContext(publisher, reactorContext));
              StepVerifier.create(result).then(emit).expectNext("value").verifyComplete();
            } else {
              StepVerifier.create(writeContext(source, reactorContext))
                  .then(emit)
                  .expectNext("value")
                  .verifyComplete();
            }
            assertThat(Span.current().getSpanContext().isValid()).isTrue();
            assertThat(Context.current().get(APPLICATION_KEY)).isEqualTo("ambient");
            agentState.assertPresent(true);
          }
        });

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("ambient").hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("signal").hasNoParent(),
                span -> span.hasName("TracedWithSpan.mono").hasParent(trace.getSpan(0))));
  }

  @Test
  void noCaptureIsSharedWithApplicationTracingSubscriber() {
    reactor.util.context.Context noCapture =
        ContextPropagationOperator.withoutSubscriptionContextCapture(
            reactor.util.context.Context.of("retained", "value"));
    assertThat(noCapture.<String>get("retained")).isEqualTo("value");
    assertThat(ContextPropagationOperator.getOpenTelemetryContext(noCapture, null)).isNull();

    Context ambient = Context.root().with(APPLICATION_KEY, "ambient");
    Context signal = Context.root().with(APPLICATION_KEY, "signal");
    assertApplicationSubscriberContext(noCapture, ambient, signal, "signal");

    Context explicit = Context.root();
    reactor.util.context.Context stored =
        ContextPropagationOperator.storeOpenTelemetryContext(noCapture, explicit);
    assertThat(stored.<String>get("retained")).isEqualTo("value");
    Context storedContext =
        requireNonNull(ContextPropagationOperator.getOpenTelemetryContext(stored, null));
    assertThat(Span.fromContext(storedContext).getSpanContext().isValid()).isFalse();
    assertApplicationSubscriberContext(stored, ambient, signal, null);
  }

  private static void assertApplicationSubscriberContext(
      reactor.util.context.Context reactorContext,
      Context subscriptionContext,
      Context signalContext,
      String expectedApplicationValue) {
    List<String> observed = new ArrayList<>();
    Subscriber<String> actual =
        new Subscriber<String>() {
          @Override
          public void onSubscribe(Subscription subscription) {
            observed.add(Context.current().get(APPLICATION_KEY));
          }

          @Override
          public void onNext(String value) {
            observed.add(Context.current().get(APPLICATION_KEY));
          }

          @Override
          public void onError(Throwable error) {
            observed.add(Context.current().get(APPLICATION_KEY));
          }

          @Override
          public void onComplete() {
            observed.add(Context.current().get(APPLICATION_KEY));
          }
        };
    TracingSubscriber<String> subscriber;
    try (Scope ignored = subscriptionContext.makeCurrent()) {
      subscriber = new TracingSubscriber<>(actual, reactorContext);
    }
    try (Scope ignored = signalContext.makeCurrent()) {
      subscriber.onSubscribe(
          new Subscription() {
            @Override
            public void request(long count) {}

            @Override
            public void cancel() {}
          });
      subscriber.onNext("value");
      subscriber.onComplete();
      assertThat(Context.current().get(APPLICATION_KEY))
          .isEqualTo(signalContext.get(APPLICATION_KEY));
    }
    assertThat(observed)
        .containsExactly(
            expectedApplicationValue, expectedApplicationValue, expectedApplicationValue);
  }

  @Test
  void explicitRootContextOverridesApplicationBoundary() {
    Context applicationContext = Context.root().with(APPLICATION_KEY, "application");
    Context operationContext = Context.root().with(APPLICATION_KEY, "operation");
    reactor.util.context.Context innerReactorContext =
        ContextPropagationOperator.storeOpenTelemetryContext(
            reactor.util.context.Context.of("retained", "value"), Context.root());
    ContextPropagationOperator operator = ContextPropagationOperator.create();
    operator.resetOnEachOperator();
    operator.registerOnEachOperator();
    try {
      try (Scope ignored = Context.root().with(APPLICATION_KEY, "subscription").makeCurrent()) {
        ContextPropagationOperator.runWithContext(
                Mono.just("value"), operationContext, applicationContext)
            .map(value -> value)
            .doOnNext(
                value -> {
                  writeContext(
                          Mono.just(value)
                              .map(
                                  innerValue -> {
                                    assertThat(Context.current().get(APPLICATION_KEY)).isNull();
                                    return innerValue;
                                  }),
                          innerReactorContext)
                      .block();
                  assertThat(Context.current().get(APPLICATION_KEY)).isEqualTo("subscription");
                })
            .block();
      }
    } finally {
      operator.resetOnEachOperator();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void runWithContextsBridgesOperationAndApplicationContexts(boolean flux) {
    testing.runWithSpan(
        "application",
        () -> {
          AgentContextState agentState = new AgentContextState();
          try (Scope ignored =
                  Context.current().with(APPLICATION_KEY, "application").makeCurrent();
              Scope agentScope = agentState.makeCurrent()) {
            Context applicationContext = Context.current();
            Mono<String> source =
                Mono.defer(
                    () -> {
                      assertThat(Span.current().getSpanContext().isValid()).isFalse();
                      assertThat(Context.current().get(APPLICATION_KEY)).isEqualTo("operation");
                      agentState.assertPresent(false);
                      return new ExtensionAnnotationsTracedWithSpan().mono(Mono.just("value"));
                    });
            Context operationContext = Context.root().with(APPLICATION_KEY, "operation");
            if (flux) {
              StepVerifier.create(
                      ContextPropagationOperator.runWithContext(
                              Flux.from(source), operationContext, applicationContext)
                          .doOnNext(unused -> assertApplicationContext(agentState)))
                  .expectNext("value")
                  .verifyComplete();
            } else {
              StepVerifier.create(
                      ContextPropagationOperator.runWithContext(
                              source, operationContext, applicationContext)
                          .doOnNext(unused -> assertApplicationContext(agentState)))
                  .expectNext("value")
                  .verifyComplete();
            }
            assertApplicationContext(agentState);
          }
        });

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("application").hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("TracedWithSpan.mono").hasNoParent()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void runWithContextsClearsAgentContextForRootApplicationContext(boolean flux) {
    Span operation =
        testing.getOpenTelemetry().getTracer("test").spanBuilder("operation").startSpan();
    try {
      testing.runWithSpan(
          "ambient",
          () -> {
            AgentContextState agentState = new AgentContextState();
            try (Scope ignored = Context.current().with(APPLICATION_KEY, "ambient").makeCurrent();
                Scope agentScope = agentState.makeCurrent()) {
              Mono<String> source =
                  Mono.defer(
                      () -> {
                        assertThat(Span.current().getSpanContext())
                            .isEqualTo(operation.getSpanContext());
                        agentState.assertPresent(false);
                        return new ExtensionAnnotationsTracedWithSpan().mono(Mono.just("value"));
                      });
              Context operationContext = Context.root().with(operation);
              if (flux) {
                StepVerifier.create(
                        ContextPropagationOperator.runWithContext(
                                Flux.from(source), operationContext, Context.root())
                            .doOnNext(unused -> assertRootContext(agentState)))
                    .expectNext("value")
                    .verifyComplete();
              } else {
                StepVerifier.create(
                        ContextPropagationOperator.runWithContext(
                                source, operationContext, Context.root())
                            .doOnNext(unused -> assertRootContext(agentState)))
                    .expectNext("value")
                    .verifyComplete();
              }
              assertThat(Span.current().getSpanContext().isValid()).isTrue();
              assertThat(Context.current().get(APPLICATION_KEY)).isEqualTo("ambient");
              agentState.assertPresent(true);
            }
          });
    } finally {
      operation.end();
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("operation").hasNoParent(),
                span -> span.hasName("TracedWithSpan.mono").hasParent(trace.getSpan(0))),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("ambient").hasNoParent()));
  }

  @ParameterizedTest
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void runWithContextsPreservesApplicationTracingSubscribersCapturedContext(
      boolean flux, boolean rootApplicationContext) {
    Context capturedContext = Context.root().with(APPLICATION_KEY, "captured");
    Context applicationContext =
        rootApplicationContext
            ? Context.root()
            : Context.root().with(APPLICATION_KEY, "application");
    Context operationContext = Context.root().with(APPLICATION_KEY, "operation");
    List<String> observed = new ArrayList<>();
    Subscriber<String> actual =
        new Subscriber<String>() {
          @Override
          public void onSubscribe(Subscription subscription) {
            observed.add(Context.current().get(APPLICATION_KEY));
            subscription.request(1);
          }

          @Override
          public void onNext(String value) {
            observed.add(Context.current().get(APPLICATION_KEY));
          }

          @Override
          public void onError(Throwable error) {
            observed.add(Context.current().get(APPLICATION_KEY));
          }

          @Override
          public void onComplete() {
            observed.add(Context.current().get(APPLICATION_KEY));
          }
        };
    TracingSubscriber<String> subscriber;
    try (Scope ignored = capturedContext.makeCurrent()) {
      subscriber = new TracingSubscriber<String>(actual, reactor.util.context.Context.empty()) {};
      Mono<String> source =
          Mono.fromCallable(
              () -> {
                assertThat(Context.current().get(APPLICATION_KEY)).isEqualTo("operation");
                return "value";
              });
      if (flux) {
        ContextPropagationOperator.runWithContext(
                Flux.from(source), operationContext, applicationContext)
            .subscribe(subscriber);
      } else {
        ContextPropagationOperator.runWithContext(source, operationContext, applicationContext)
            .subscribe(subscriber);
      }
      assertThat(Context.current().get(APPLICATION_KEY)).isEqualTo("captured");
    }

    assertThat(observed).containsExactly("captured", "captured", "captured");
  }

  private static void assertRootContext(AgentContextState agentState) {
    assertThat(Span.current().getSpanContext().isValid()).isFalse();
    assertThat(Context.current().get(APPLICATION_KEY)).isNull();
    agentState.assertPresent(false);
  }

  private static void assertApplicationContext(AgentContextState agentState) {
    assertThat(Span.current().getSpanContext().isValid()).isTrue();
    assertThat(Context.current().get(APPLICATION_KEY)).isEqualTo("application");
    agentState.assertPresent(true);
  }

  @SuppressWarnings("unchecked")
  private static <T> Mono<T> writeContext(
      Mono<T> source, reactor.util.context.Context reactorContext) {
    try {
      return (Mono<T>)
          MONO_CONTEXT_WRITE_METHOD.invoke(
              source,
              (Function<reactor.util.context.Context, reactor.util.context.Context>)
                  ignored -> reactorContext);
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> Flux<T> writeContext(
      Flux<T> source, reactor.util.context.Context reactorContext) {
    try {
      return (Flux<T>)
          getContextWriteMethod(Flux.class)
              .invoke(
                  source,
                  (Function<reactor.util.context.Context, reactor.util.context.Context>)
                      ignored -> reactorContext);
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  @Test
  void runMonoWithContextMakesItCurrent() {
    Mono<String> result =
        Mono.defer(
            () -> {
              Span span =
                  testing.getOpenTelemetry().getTracer("test").spanBuilder("parent").startSpan();
              Mono<String> outer =
                  Mono.defer(
                      () -> new ExtensionAnnotationsTracedWithSpan().mono(Mono.just("Value")));
              return ContextPropagationOperator.runWithContext(outer, Context.current().with(span))
                  .doFinally(unused -> span.end());
            });

    StepVerifier.create(result).expectNext("Value").verifyComplete();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("TracedWithSpan.mono")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void runFluxWithContextMakesItCurrent() {
    Flux<String> result =
        Flux.defer(
            () -> {
              Span span =
                  testing.getOpenTelemetry().getTracer("test").spanBuilder("parent").startSpan();
              Flux<String> outer =
                  Flux.defer(
                      () -> new ExtensionAnnotationsTracedWithSpan().flux(Flux.just("Value")));
              return ContextPropagationOperator.runWithContext(outer, Context.current().with(span))
                  .doFinally(unused -> span.end());
            });

    StepVerifier.create(result).expectNext("Value").verifyComplete();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("TracedWithSpan.flux")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void storeContextForcesItToBecomeCurrent() {
    Mono<String> result =
        Mono.defer(
            () -> {
              Span span =
                  testing.getOpenTelemetry().getTracer("test").spanBuilder("parent").startSpan();

              Mono<String> interim =
                  Mono.delay(Duration.ofMillis(1))
                      .flatMap(
                          unused -> {
                            // usual trick to force this to run under new TracingSubscriber with
                            // context
                            // written in the next call
                            return new ExtensionAnnotationsTracedWithSpan()
                                .mono(Mono.just("Value"));
                          });
              try {
                interim =
                    (Mono<String>)
                        MONO_CONTEXT_WRITE_METHOD.invoke(
                            interim, new StoreOpenTelemetryContext(span));
              } catch (Throwable t) {
                throw new RuntimeException(t);
              }
              return interim.doFinally(unused -> span.end());
            });

    StepVerifier.create(result).expectNext("Value").verifyComplete();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("TracedWithSpan.mono")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  private static class StoreOpenTelemetryContext
      implements Function<reactor.util.context.Context, reactor.util.context.Context> {

    private final Span span;

    private StoreOpenTelemetryContext(Span span) {
      this.span = span;
    }

    @Override
    public reactor.util.context.Context apply(reactor.util.context.Context context) {
      return ContextPropagationOperator.storeOpenTelemetryContext(
          context, Context.current().with(span));
    }
  }

  private static class AgentContextState {
    private final Class<?> contextClass;
    private final Object key;
    private final Object suppressionKey;
    private final Method get;
    private final Method fromContext;
    private final Method with;
    private final Method storeInContext;
    private final Object span;

    AgentContextState() {
      try {
        contextClass =
            Class.forName("io.opentelemetry.javaagent.shaded.io.opentelemetry.context.Context");
        Class<?> keyClass =
            Class.forName("io.opentelemetry.javaagent.shaded.io.opentelemetry.context.ContextKey");
        Class<?> spanClass =
            Class.forName("io.opentelemetry.javaagent.shaded.io.opentelemetry.api.trace.Span");
        Class<?> spanKeyClass =
            Class.forName("io.opentelemetry.javaagent.shaded.instrumentation.api.internal.SpanKey");
        key = keyClass.getMethod("named", String.class).invoke(null, "agent-only");
        suppressionKey = spanKeyClass.getField("KIND_CLIENT").get(null);
        get = contextClass.getMethod("get", keyClass);
        fromContext = spanKeyClass.getMethod("fromContextOrNull", contextClass);
        with = contextClass.getMethod("with", keyClass, Object.class);
        storeInContext = spanKeyClass.getMethod("storeInContext", contextClass, spanClass);
        span = spanClass.getMethod("current").invoke(null);
      } catch (ReflectiveOperationException e) {
        throw new LinkageError(e.getMessage(), e);
      }
    }

    Scope makeCurrent() {
      try {
        Object current = contextClass.getMethod("current").invoke(null);
        Object withValue = with.invoke(current, key, "agent");
        Object context = storeInContext.invoke(suppressionKey, withValue, span);
        Object scope = contextClass.getMethod("makeCurrent").invoke(context);
        Method close =
            Class.forName("io.opentelemetry.javaagent.shaded.io.opentelemetry.context.Scope")
                .getMethod("close");
        return () -> {
          try {
            close.invoke(scope);
          } catch (ReflectiveOperationException e) {
            throw new LinkageError(e.getMessage(), e);
          }
        };
      } catch (ReflectiveOperationException e) {
        throw new LinkageError(e.getMessage(), e);
      }
    }

    void assertPresent(boolean present) {
      try {
        Object current = contextClass.getMethod("current").invoke(null);
        assertThat(get.invoke(current, key)).isEqualTo(present ? "agent" : null);
        assertThat(fromContext.invoke(suppressionKey, current) != null).isEqualTo(present);
      } catch (ReflectiveOperationException e) {
        throw new LinkageError(e.getMessage(), e);
      }
    }
  }
}
