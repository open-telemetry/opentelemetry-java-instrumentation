/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.reactor;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategies;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.reactivestreams.Publisher;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.core.Scannable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;

/** Based on Spring Sleuth's Reactor instrumentation. */
public final class ContextPropagationOperator {

  public static ContextPropagationOperator create() {
    return builder().build();
  }

  public static ContextPropagationOperatorBuilder builder() {
    return new ContextPropagationOperatorBuilder();
  }

  private final ReactorAsyncOperationEndStrategy asyncOperationEndStrategy;

  private static final Object TRACE_CONTEXT_KEY =
      new Object() {
        @Override
        public String toString() {
          return "otel-trace-context";
        }
      };

  private static volatile boolean enabled = false;

  /**
   * Stores Trace {@link io.opentelemetry.context.Context} in Reactor {@link
   * reactor.util.context.Context}.
   *
   * @param context Reactor's context to store trace context in.
   * @param traceContext Trace context to be stored.
   */
  public static reactor.util.context.Context storeOpenTelemetryContext(
      reactor.util.context.Context context, Context traceContext) {
    return context.put(TRACE_CONTEXT_KEY, traceContext);
  }

  /**
   * Gets Trace {@link io.opentelemetry.context.Context} from Reactor {@link
   * reactor.util.context.Context}.
   *
   * @param context Reactor's context to get trace context from.
   * @param defaultTraceContext Default value to be returned if no trace context is found on Reactor
   *     context.
   * @return Trace context or default value.
   */
  public static Context getOpenTelemetryContext(
      reactor.util.context.Context context, Context defaultTraceContext) {
    return context.getOrDefault(TRACE_CONTEXT_KEY, defaultTraceContext);
  }

  ContextPropagationOperator(boolean captureExperimentalSpanAttributes) {
    this.asyncOperationEndStrategy =
        ReactorAsyncOperationEndStrategy.builder()
            .setCaptureExperimentalSpanAttributes(captureExperimentalSpanAttributes)
            .build();
  }

  /**
   * Registers a hook that applies to every operator, propagating {@link Context} to downstream
   * callbacks to ensure spans in the {@link Context} are available throughout the lifetime of a
   * reactive stream. This should generally be called in a static initializer block in your
   * application.
   */
  public void registerOnEachOperator() {
    Hooks.onEachOperator(TracingSubscriber.class.getName(), tracingLift(asyncOperationEndStrategy));
    AsyncOperationEndStrategies.instance().registerStrategy(asyncOperationEndStrategy);
    enabled = true;
  }

  /** Unregisters the hook registered by {@link #registerOnEachOperator()}. */
  public void resetOnEachOperator() {
    Hooks.resetOnEachOperator(TracingSubscriber.class.getName());
    AsyncOperationEndStrategies.instance().unregisterStrategy(asyncOperationEndStrategy);
    enabled = false;
  }

  private static <T> Function<? super Publisher<T>, ? extends Publisher<T>> tracingLift(
      ReactorAsyncOperationEndStrategy asyncOperationEndStrategy) {
    return Operators.lift(new Lifter<>(asyncOperationEndStrategy));
  }

  /** Forces Mono to run in traceContext scope. */
  static <T> Mono<T> runWithContext(Mono<T> publisher, Context tracingContext) {
    if (!enabled) {
      return publisher;
    }

    // this hack forces 'publisher' to run in the onNext callback of `TracingSubscriber`
    // (created for this publisher) and with current() span that refers to span created here
    // without the hack, publisher runs in the onAssembly stage, before traceContext is made current
    return ScalarPropagatingMono.INSTANCE
        .flatMap(i -> publisher)
        .subscriberContext(ctx -> storeOpenTelemetryContext(ctx, tracingContext));
  }

  /** Forces Flux to run in traceContext scope. */
  static <T> Flux<T> runWithContext(Flux<T> publisher, Context tracingContext) {
    if (!enabled) {
      return publisher;
    }

    // this hack forces 'publisher' to run in the onNext callback of `TracingSubscriber`
    // (created for this publisher) and with current() span that refers to span created here
    // without the hack, publisher runs in the onAssembly stage, before traceContext is made current
    return ScalarPropagatingFlux.INSTANCE
        .flatMap(i -> publisher)
        .subscriberContext(ctx -> storeOpenTelemetryContext(ctx, tracingContext));
  }

  public static class Lifter<T>
      implements BiFunction<Scannable, CoreSubscriber<? super T>, CoreSubscriber<? super T>> {

    /** Holds reference to strategy to prevent it from being collected. */
    @SuppressWarnings("FieldCanBeLocal")
    private final ReactorAsyncOperationEndStrategy asyncOperationEndStrategy;

    public Lifter(ReactorAsyncOperationEndStrategy asyncOperationEndStrategy) {
      this.asyncOperationEndStrategy = asyncOperationEndStrategy;
    }

    @Override
    public CoreSubscriber<? super T> apply(Scannable publisher, CoreSubscriber<? super T> sub) {
      // if Flux/Mono #just, #empty, #error
      if (publisher instanceof Fuseable.ScalarCallable) {
        return sub;
      }
      return new TracingSubscriber<>(sub, sub.currentContext());
    }
  }

  static void subscribeInActiveSpan(CoreSubscriber<? super Object> actual, Object value) {
    Context tracingContextInReactor =
        ContextPropagationOperator.getOpenTelemetryContext(actual.currentContext(), null);
    if (tracingContextInReactor == null || tracingContextInReactor == Context.current()) {
      actual.onSubscribe(Operators.scalarSubscription(actual, value));
    } else {
      try (Scope ignored = tracingContextInReactor.makeCurrent()) {
        actual.onSubscribe(Operators.scalarSubscription(actual, value));
      }
    }
  }

  static class ScalarPropagatingMono extends Mono<Object> {
    public static final Mono<Object> INSTANCE = new ScalarPropagatingMono();

    private final Object value = new Object();

    private ScalarPropagatingMono() {}

    @Override
    public void subscribe(CoreSubscriber<? super Object> actual) {
      subscribeInActiveSpan(actual, value);
    }
  }

  static class ScalarPropagatingFlux extends Flux<Object> {
    public static final Flux<Object> INSTANCE = new ScalarPropagatingFlux();

    private final Object value = new Object();

    private ScalarPropagatingFlux() {}

    @Override
    public void subscribe(CoreSubscriber<? super Object> actual) {
      subscribeInActiveSpan(actual, value);
    }
  }
}
