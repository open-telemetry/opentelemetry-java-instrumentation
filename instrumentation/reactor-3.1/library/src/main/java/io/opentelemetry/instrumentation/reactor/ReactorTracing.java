/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

import io.opentelemetry.context.Context;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReactorTracing {

  private static final Object TRACE_CONTEXT_KEY =
      new Object() {
        @Override
        public String toString() {
          return "otel-trace-context";
        }
      };

  static volatile Mono<String> dummyMono;
  static volatile Flux<String> dummyFlux;

  static synchronized void enable() {
    // have to be reset as they capture async strategy and lift
    dummyMono = Mono.just("");
    dummyFlux = Flux.just("");
  }

  static synchronized void disable() {
    // have to be reset as they capture async strategy and lift
    dummyMono = Mono.just("");
    dummyFlux = Flux.just("");
  }

  /** Forces Mono to run in traceContext scope. */
  static <T> Mono<T> runInScope(Mono<T> publisher, Context tracingContext) {
    // this hack forces 'publisher' to run in the onNext callback of `TracingSubscriber`
    // created for this publisher and with current() span that refer to span created here
    // without the hack, publisher runs in the onAssembly stage, before traceContext is made current
    return dummyMono
        .flatMap(i -> publisher)
        .subscriberContext(ctx -> storeOpenTelemetryContext(ctx, tracingContext));
  }

  /** Forces Flux to run in traceContext scope. */
  static <T> Flux<T> runInScope(Flux<T> publisher, Context tracingContext) {
    // this hack forces 'publisher' to run in the onNext callback of `TracingSubscriber`
    // created for this publisher and with current() span that refer to span created here
    // without the hack, publisher runs in the onAssembly stage, before traceContext is made current
    return dummyFlux
        .flatMap(i -> publisher)
        .subscriberContext(ctx -> storeOpenTelemetryContext(ctx, tracingContext));
  }

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
}
