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
import io.opentelemetry.context.ContextKey;
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
public final class TracingOperator {

  public static TracingOperator create() {
    return newBuilder().build();
  }

  public static TracingOperatorBuilder newBuilder() {
    return new TracingOperatorBuilder();
  }

  private static final ContextKey<Context> TRACE_CONTEXT_KEY =
      ContextKey.named("otel-trace-context");

  // have to be re-initialized after hooks are set/reset
  private static Mono<String> dummyMono = Mono.just("");
  private static Flux<String> dummyFlux = Flux.just("");

  private final ReactorAsyncOperationEndStrategy asyncOperationEndStrategy;

  TracingOperator(boolean captureExperimentalSpanAttributes) {
    this.asyncOperationEndStrategy =
        ReactorAsyncOperationEndStrategy.newBuilder()
            .setCaptureExperimentalSpanAttributes(captureExperimentalSpanAttributes)
            .build();
  }

  /** Forces Mono to run in traceContext scope. */
  static <T> Mono<T> runInScope(Mono<T> publisher, Context tracingContext) {
    // this hack forces 'publisher' to run in the onNext callback of `TracingSubscriber`
    // created for this publisher and with current() span that refer to span created here
    // without the hack, publisher runs in the onAssembly stage, before traceContext is made current
    return dummyMono
        .flatMap(i -> publisher)
        .subscriberContext(ctx -> storeInContext(ctx, tracingContext));
  }

  /** Forces Flux to run in traceContext scope. */
  static <T> Flux<T> runInScope(Flux<T> publisher, Context tracingContext) {
    // this hack forces 'publisher' to run in the onNext callback of `TracingSubscriber`
    // created for this publisher and with current() span that refer to span created here
    // without the hack, publisher runs in the onAssembly stage, before traceContext is made current
    return dummyFlux
        .flatMap(i -> publisher)
        .subscriberContext(ctx -> storeInContext(ctx, tracingContext));
  }

  /**
   * Stores Trace {@link io.opentelemetry.context.Context} in Reactor {@link
   * reactor.util.context.Context}.
   *
   * @param context Reactor's context to store trace context in.
   * @param traceContext Trace context to be stored.
   */
  public static reactor.util.context.Context storeInContext(
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
  public static Context fromContextOrDefault(
      reactor.util.context.Context context, Context defaultTraceContext) {
    return context.getOrDefault(TRACE_CONTEXT_KEY, defaultTraceContext);
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
    dummyMono = Mono.just("");
    dummyFlux = Flux.just("");
  }

  /** Unregisters the hook registered by {@link #registerOnEachOperator()}. */
  public void resetOnEachOperator() {
    Hooks.resetOnEachOperator(TracingSubscriber.class.getName());
    AsyncOperationEndStrategies.instance().unregisterStrategy(asyncOperationEndStrategy);
    dummyMono = Mono.just("");
    dummyFlux = Flux.just("");
  }

  private static <T> Function<? super Publisher<T>, ? extends Publisher<T>> tracingLift(
      ReactorAsyncOperationEndStrategy asyncOperationEndStrategy) {
    return Operators.lift(new Lifter<>(asyncOperationEndStrategy));
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
}
