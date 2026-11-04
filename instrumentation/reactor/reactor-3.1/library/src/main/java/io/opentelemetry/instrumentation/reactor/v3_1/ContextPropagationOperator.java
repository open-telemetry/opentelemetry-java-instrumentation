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

package io.opentelemetry.instrumentation.reactor.v3_1;

import static java.lang.invoke.MethodType.methodType;
import static java.util.logging.Level.WARNING;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategies;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Scannable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.ContextView;

/** Based on Spring Sleuth's Reactor instrumentation. */
public final class ContextPropagationOperator {
  private static final Logger logger = Logger.getLogger(ContextPropagationOperator.class.getName());

  private static final Object VALUE = new Object();

  private static final String SCHEDULERS_HOOK_KEY = RunnableWrapper.class.getName();

  @Nullable
  private static final MethodHandle MONO_CONTEXT_WRITE_METHOD = getContextWriteMethod(Mono.class);

  @Nullable
  private static final MethodHandle FLUX_CONTEXT_WRITE_METHOD = getContextWriteMethod(Flux.class);

  @Nullable private static final MethodHandle SCHEDULERS_HOOK_METHOD = getSchedulersHookMethod();

  @Nullable
  private static final MethodHandle SCHEDULERS_RESET_HOOK_METHOD = getSchedulersResetHookMethod();

  private static final Object TRACE_CONTEXT_KEY =
      new Object() {
        @Override
        public String toString() {
          return "otel-trace-context";
        }
      };

  private static final Object NO_SUBSCRIPTION_CONTEXT_CAPTURE_KEY = new Object();

  private static final Object lock = new Object();

  private static volatile boolean enabled = false;

  private final ReactorAsyncOperationEndStrategy asyncOperationEndStrategy;

  @Nullable
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

  @Nullable
  private static MethodHandle getSchedulersHookMethod() {
    MethodHandles.Lookup lookup = MethodHandles.publicLookup();
    try {
      return lookup.findStatic(
          Schedulers.class, "onScheduleHook", methodType(void.class, String.class, Function.class));
    } catch (NoSuchMethodException | IllegalAccessException ignored) {
      // ignore
    }
    return null;
  }

  @Nullable
  private static MethodHandle getSchedulersResetHookMethod() {
    MethodHandles.Lookup lookup = MethodHandles.publicLookup();
    try {
      return lookup.findStatic(
          Schedulers.class, "resetOnScheduleHook", methodType(void.class, String.class));
    } catch (NoSuchMethodException | IllegalAccessException ignored) {
      // ignore
    }
    return null;
  }

  public static ContextPropagationOperator create() {
    return builder().build();
  }

  public static ContextPropagationOperatorBuilder builder() {
    return new ContextPropagationOperatorBuilder();
  }

  /**
   * Stores Trace {@link io.opentelemetry.context.Context} in Reactor {@link
   * reactor.util.context.Context}. The stored context is authoritative during signals, including
   * {@link Context#root()}, and replaces any {@link #withoutSubscriptionContextCapture} state.
   *
   * @param context Reactor's context to store trace context in.
   * @param traceContext Trace context to be stored.
   */
  public static reactor.util.context.Context storeOpenTelemetryContext(
      reactor.util.context.Context context, Context traceContext) {
    return context.delete(NO_SUBSCRIPTION_CONTEXT_CAPTURE_KEY).put(TRACE_CONTEXT_KEY, traceContext);
  }

  /**
   * Removes any stored OpenTelemetry context and disables implicit capture of the context active at
   * subscription time. Unlike storing {@link Context#root()}, this leaves the context active during
   * signals unchanged.
   *
   * <p>A subsequent call to {@link #storeOpenTelemetryContext} replaces this behavior.
   */
  public static reactor.util.context.Context withoutSubscriptionContextCapture(
      reactor.util.context.Context context) {
    return context.delete(TRACE_CONTEXT_KEY).put(NO_SUBSCRIPTION_CONTEXT_CAPTURE_KEY, true);
  }

  static boolean isSubscriptionContextCaptureDisabled(reactor.util.context.Context context) {
    return context.hasKey(NO_SUBSCRIPTION_CONTEXT_CAPTURE_KEY);
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
  @Nullable
  public static Context getOpenTelemetryContext(
      reactor.util.context.Context context, @Nullable Context defaultTraceContext) {
    return context.getOrDefault(TRACE_CONTEXT_KEY, defaultTraceContext);
  }

  /**
   * Gets Trace {@link Context} from Reactor {@link reactor.util.context.ContextView}.
   *
   * @param contextView Reactor's context to get trace context from.
   * @param defaultTraceContext Default value to be returned if no trace context is found on Reactor
   *     context.
   * @return Trace context or default value.
   */
  @NoMuzzle
  @Nullable
  public static Context getOpenTelemetryContextFromContextView(
      ContextView contextView, @Nullable Context defaultTraceContext) {
    return contextView.getOrDefault(TRACE_CONTEXT_KEY, defaultTraceContext);
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
    synchronized (lock) {
      if (enabled) {
        return;
      }
      Hooks.onEachOperator(
          TracingSubscriber.class.getName(), tracingLift(asyncOperationEndStrategy));
      AsyncOperationEndStrategies.instance().registerStrategy(asyncOperationEndStrategy);
      registerScheduleHook(SCHEDULERS_HOOK_KEY, RunnableWrapper::new);
      enabled = true;
    }
  }

  private static void registerScheduleHook(String key, Function<Runnable, Runnable> function) {
    if (SCHEDULERS_HOOK_METHOD == null) {
      return;
    }
    try {
      SCHEDULERS_HOOK_METHOD.invoke(key, function);
    } catch (Throwable t) {
      logger.log(WARNING, "Failed to install scheduler hook", t);
    }
  }

  private static void resetScheduleHook(String key) {
    if (SCHEDULERS_RESET_HOOK_METHOD == null) {
      return;
    }
    try {
      SCHEDULERS_RESET_HOOK_METHOD.invoke(key);
    } catch (Throwable t) {
      logger.log(WARNING, "Failed to remove scheduler hook", t);
    }
  }

  /** Unregisters the hook registered by {@link #registerOnEachOperator()}. */
  public void resetOnEachOperator() {
    synchronized (lock) {
      if (!enabled) {
        return;
      }
      Hooks.resetOnEachOperator(TracingSubscriber.class.getName());
      AsyncOperationEndStrategies.instance().unregisterStrategy(asyncOperationEndStrategy);
      resetScheduleHook(SCHEDULERS_HOOK_KEY);
      enabled = false;
    }
  }

  private static <T> Function<? super Publisher<T>, ? extends Publisher<T>> tracingLift(
      ReactorAsyncOperationEndStrategy asyncOperationEndStrategy) {
    return Operators.lift(new Lifter<>(asyncOperationEndStrategy));
  }

  /** Forces Mono to run in traceContext scope. */
  @SuppressWarnings("unchecked")
  public static <T> Mono<T> runWithContext(Mono<T> publisher, Context tracingContext) {
    if (!enabled || MONO_CONTEXT_WRITE_METHOD == null) {
      return publisher;
    }

    // this hack forces 'publisher' to run in the onNext callback of `TracingSubscriber`
    // (created for this publisher) and with current() span that refers to span created here
    // without the hack, publisher runs in the onAssembly stage, before traceContext is made current
    try {
      return (Mono<T>)
          MONO_CONTEXT_WRITE_METHOD.invoke(
              ScalarPropagatingMono.create(publisher),
              new StoreOpenTelemetryContext(tracingContext));
    } catch (Throwable t) {
      // rethrowing without any wrapping to avoid any change to the underlying application behavior
      throw sneakyThrow(t);
    }
  }

  /** Forces Flux to run in traceContext scope. */
  @SuppressWarnings("unchecked")
  public static <T> Flux<T> runWithContext(Flux<T> publisher, Context tracingContext) {
    if (!enabled || FLUX_CONTEXT_WRITE_METHOD == null) {
      return publisher;
    }

    // this hack forces 'publisher' to run in the onNext callback of `TracingSubscriber`
    // (created for this publisher) and with current() span that refers to span created here
    // without the hack, publisher runs in the onAssembly stage, before traceContext is made current
    try {
      return (Flux<T>)
          FLUX_CONTEXT_WRITE_METHOD.invoke(
              ScalarPropagatingFlux.create(publisher),
              new StoreOpenTelemetryContext(tracingContext));
    } catch (Throwable t) {
      // rethrowing without any wrapping to avoid any change to the underlying application behavior
      throw sneakyThrow(t);
    }
  }

  /**
   * Runs the source subscription, upstream Reactor context lookup, and request and cancellation
   * calls in {@code operationContext}, and callbacks to the immediate downstream subscriber in
   * {@code applicationContext}, including when either context is {@link Context#root()}.
   *
   * <p>The source sees {@code operationContext} in its Reactor context, preserving all unrelated
   * entries. Downstream tracing subscribers retain their own context policy, so this boundary does
   * not associate an application context with values across fan-in operators. It works without
   * registering propagation hooks; it does not propagate context across arbitrary asynchronous work
   * inside the source.
   */
  public static <T> Mono<T> runWithContext(
      Mono<T> source, Context operationContext, Context applicationContext) {
    return new ContextBoundaryMono<>(source, operationContext, applicationContext);
  }

  /**
   * Runs the source subscription, upstream Reactor context lookup, and request and cancellation
   * calls in {@code operationContext}, and callbacks to the immediate downstream subscriber in
   * {@code applicationContext}, including when either context is {@link Context#root()}.
   *
   * <p>The source sees {@code operationContext} in its Reactor context, preserving all unrelated
   * entries. Downstream tracing subscribers retain their own context policy, so this boundary does
   * not associate an application context with values across fan-in operators. It works without
   * registering propagation hooks; it does not propagate context across arbitrary asynchronous work
   * inside the source.
   */
  public static <T> Flux<T> runWithContext(
      Flux<T> source, Context operationContext, Context applicationContext) {
    return new ContextBoundaryFlux<>(source, operationContext, applicationContext);
  }

  @SuppressWarnings({"TypeParameterUnusedInFormals", "unchecked"}) // fine
  private static <T extends Throwable> T sneakyThrow(Throwable t) throws T {
    throw (T) t;
  }

  private static class StoreOpenTelemetryContext
      implements Function<reactor.util.context.Context, reactor.util.context.Context> {

    private final Context tracingContext;

    private StoreOpenTelemetryContext(Context tracingContext) {
      this.tracingContext = tracingContext;
    }

    @Override
    public reactor.util.context.Context apply(reactor.util.context.Context context) {
      return storeOpenTelemetryContext(context, tracingContext);
    }
  }

  private static final class ContextBoundaryMono<T> extends Mono<T> implements Scannable {
    private final Mono<T> source;
    private final Context operationContext;
    private final Context applicationContext;

    private ContextBoundaryMono(
        Mono<T> source, Context operationContext, Context applicationContext) {
      this.source = source;
      this.operationContext = operationContext;
      this.applicationContext = applicationContext;
    }

    @Override
    public void subscribe(CoreSubscriber<? super T> actual) {
      try (Scope ignored = operationContext.makeCurrent()) {
        source.subscribe(
            new ContextBoundarySubscriber<>(actual, operationContext, applicationContext));
      }
    }

    @Override
    @Nullable
    @SuppressWarnings("rawtypes") // Reactor's Scannable contract exposes a raw Attr.
    public Object scanUnsafe(Attr attr) {
      return attr == Attr.PARENT ? source : null;
    }
  }

  private static final class ContextBoundaryFlux<T> extends Flux<T> implements Scannable {
    private final Flux<T> source;
    private final Context operationContext;
    private final Context applicationContext;

    private ContextBoundaryFlux(
        Flux<T> source, Context operationContext, Context applicationContext) {
      this.source = source;
      this.operationContext = operationContext;
      this.applicationContext = applicationContext;
    }

    @Override
    public void subscribe(CoreSubscriber<? super T> actual) {
      try (Scope ignored = operationContext.makeCurrent()) {
        source.subscribe(
            new ContextBoundarySubscriber<>(actual, operationContext, applicationContext));
      }
    }

    @Override
    @Nullable
    @SuppressWarnings("rawtypes") // Reactor's Scannable contract exposes a raw Attr.
    public Object scanUnsafe(Attr attr) {
      return attr == Attr.PARENT ? source : null;
    }
  }

  private static final class ContextBoundarySubscriber<T>
      implements CoreSubscriber<T>, Subscription {
    private final CoreSubscriber<? super T> actual;
    private final Context operationContext;
    private final Context applicationContext;
    private Subscription subscription;

    private ContextBoundarySubscriber(
        CoreSubscriber<? super T> actual, Context operationContext, Context applicationContext) {
      this.actual = actual;
      this.operationContext = operationContext;
      this.applicationContext = applicationContext;
    }

    @Override
    public reactor.util.context.Context currentContext() {
      try (Scope ignored = operationContext.makeCurrent()) {
        return storeOpenTelemetryContext(actual.currentContext(), operationContext);
      }
    }

    @Override
    public void onSubscribe(Subscription subscription) {
      this.subscription = subscription;
      try (Scope ignored = applicationContext.makeCurrent()) {
        actual.onSubscribe(this);
      }
    }

    @Override
    public void onNext(T value) {
      try (Scope ignored = applicationContext.makeCurrent()) {
        actual.onNext(value);
      }
    }

    @Override
    public void onError(Throwable error) {
      try (Scope ignored = applicationContext.makeCurrent()) {
        actual.onError(error);
      }
    }

    @Override
    public void onComplete() {
      try (Scope ignored = applicationContext.makeCurrent()) {
        actual.onComplete();
      }
    }

    @Override
    public void request(long count) {
      try (Scope ignored = operationContext.makeCurrent()) {
        subscription.request(count);
      }
    }

    @Override
    public void cancel() {
      try (Scope ignored = operationContext.makeCurrent()) {
        subscription.cancel();
      }
    }
  }

  private static class Lifter<T>
      implements BiFunction<Scannable, CoreSubscriber<? super T>, CoreSubscriber<? super T>> {

    /** Holds reference to strategy to prevent it from being collected. */
    @SuppressWarnings({"FieldCanBeLocal", "UnusedVariable"})
    private final ReactorAsyncOperationEndStrategy asyncOperationEndStrategy;

    Lifter(ReactorAsyncOperationEndStrategy asyncOperationEndStrategy) {
      this.asyncOperationEndStrategy = asyncOperationEndStrategy;
    }

    @Override
    public CoreSubscriber<? super T> apply(Scannable publisher, CoreSubscriber<? super T> sub) {
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

  static class ScalarPropagatingMono extends Mono<Object> implements Scannable {

    static <T> Mono<T> create(Mono<T> source) {
      return new ScalarPropagatingMono(source).flatMap(unused -> source);
    }

    private final Mono<?> source;

    private ScalarPropagatingMono(Mono<?> source) {
      this.source = source;
    }

    @Override
    public void subscribe(CoreSubscriber<? super Object> actual) {
      subscribeInActiveSpan(actual, VALUE);
    }

    @Override
    @Nullable
    // Interface method doesn't have type parameter so we can't add it either.
    @SuppressWarnings("rawtypes")
    public Object scanUnsafe(Attr attr) {
      if (attr == Attr.PARENT) {
        return source;
      }
      return null;
    }
  }

  static class ScalarPropagatingFlux extends Mono<Object> implements Scannable {

    static <T> Flux<T> create(Flux<T> source) {
      // flatMapMany passes downstream demand to the source unchanged, while Flux.flatMap would
      // prefetch from it
      return new ScalarPropagatingFlux(source).flatMapMany(unused -> source);
    }

    private final Flux<?> source;

    private ScalarPropagatingFlux(Flux<?> source) {
      this.source = source;
    }

    @Override
    public void subscribe(CoreSubscriber<? super Object> actual) {
      subscribeInActiveSpan(actual, VALUE);
    }

    @Override
    @Nullable
    // Interface method doesn't have type parameter so we can't add it either.
    @SuppressWarnings("rawtypes")
    public Object scanUnsafe(Scannable.Attr attr) {
      if (attr == Scannable.Attr.PARENT) {
        return source;
      }
      return null;
    }
  }

  static class RunnableWrapper implements Runnable {
    private final Runnable delegate;
    private final Context context;

    RunnableWrapper(Runnable delegate) {
      this.delegate = delegate;
      context = Context.current();
    }

    @Override
    public void run() {
      try (Scope ignore = context.makeCurrent()) {
        delegate.run();
      }
    }
  }
}
