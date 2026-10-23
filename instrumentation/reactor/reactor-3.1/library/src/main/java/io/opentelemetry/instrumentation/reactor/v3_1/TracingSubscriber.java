/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2018 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.opentelemetry.instrumentation.reactor.v3_1;

import io.opentelemetry.context.Scope;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Scannable;
import reactor.util.context.Context;

/**
 * Based on OpenTracing code.
 * https://github.com/opentracing-contrib/java-reactor/blob/master/src/main/java/io/opentracing/contrib/reactor/TracedSubscriber.java
 */
public class TracingSubscriber<T> implements CoreSubscriber<T>, Scannable {
  @Nullable private static final Class<?> fluxRetrySubscriberClass = getFluxRetrySubscriberClass();

  @Nullable
  private static final Class<?> fluxRetryWhenSubscriberClass = getFluxRetryWhenSubscriberClass();

  private final io.opentelemetry.context.Context traceContext;
  private final Subscriber<? super T> subscriber;
  private final Context context;
  private final boolean hasContextToPropagate;

  /**
   * Uses an explicitly stored OpenTelemetry context, including root, or implicitly captures the
   * current context unless subscription capture is disabled. An implicitly captured root context
   * leaves the context active during signals unchanged.
   */
  public TracingSubscriber(Subscriber<? super T> subscriber, Context ctx) {
    this(subscriber, ctx, io.opentelemetry.context.Context.current(), false);
  }

  /**
   * Uses an explicitly stored OpenTelemetry context or {@code contextToPropagate} as an explicit
   * fallback. Explicit contexts, including root, are authoritative during signals. Disabling
   * subscription context capture does not suppress this explicit fallback.
   */
  public TracingSubscriber(
      Subscriber<? super T> subscriber,
      Context ctx,
      io.opentelemetry.context.Context contextToPropagate) {
    this(subscriber, ctx, contextToPropagate, true);
  }

  private TracingSubscriber(
      Subscriber<? super T> subscriber,
      Context ctx,
      io.opentelemetry.context.Context contextToPropagate,
      boolean explicitFallback) {
    this.subscriber = subscriber;
    this.context = ctx;
    io.opentelemetry.context.Context storedContext =
        ContextPropagationOperator.getOpenTelemetryContext(ctx, null);
    boolean captureDisabled =
        storedContext == null
            && !explicitFallback
            && ContextPropagationOperator.isSubscriptionContextCaptureDisabled(ctx);
    this.traceContext =
        storedContext != null
            ? storedContext
            : captureDisabled ? io.opentelemetry.context.Context.root() : contextToPropagate;
    this.hasContextToPropagate =
        storedContext != null
            || explicitFallback
            || (!captureDisabled && traceContext != io.opentelemetry.context.Context.root());
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    try (Scope ignore = openScope(hasContextToPropagate ? traceContext : null)) {
      subscriber.onSubscribe(subscription);
    }
  }

  @Override
  public void onNext(T o) {
    try (Scope ignore = openScope(hasContextToPropagate ? traceContext : null)) {
      subscriber.onNext(o);
    }
  }

  @Override
  public void onError(Throwable throwable) {
    Supplier<Scope> scopeSupplier;
    if (!hasContextToPropagate
        && (fluxRetrySubscriberClass == subscriber.getClass()
            || fluxRetryWhenSubscriberClass == subscriber.getClass())) {
      // clear context for retry to avoid having retried operations run with currently active
      // context as parent context
      scopeSupplier = () -> openScope(io.opentelemetry.context.Context.root());
    } else {
      scopeSupplier = () -> openScope(hasContextToPropagate ? traceContext : null);
    }
    try (Scope ignore = scopeSupplier.get()) {
      subscriber.onError(throwable);
    }
  }

  @Override
  public void onComplete() {
    try (Scope ignore = openScope(hasContextToPropagate ? traceContext : null)) {
      subscriber.onComplete();
    }
  }

  @Override
  public Context currentContext() {
    return context;
  }

  @Override
  @Nullable
  @SuppressWarnings("rawtypes") // Reactor's Scannable contract exposes a raw Attr.
  public Object scanUnsafe(Scannable.Attr attr) {
    return attr == Scannable.Attr.ACTUAL ? subscriber : null;
  }

  @Nullable
  private static Scope openScope(@Nullable io.opentelemetry.context.Context context) {
    return context == null ? null : context.makeCurrent();
  }

  @Nullable
  private static Class<?> getFluxRetrySubscriberClass() {
    try {
      return Class.forName("reactor.core.publisher.FluxRetry$RetrySubscriber");
    } catch (ClassNotFoundException ignored) {
      return null;
    }
  }

  @Nullable
  private static Class<?> getFluxRetryWhenSubscriberClass() {
    try {
      return Class.forName("reactor.core.publisher.FluxRetryWhen$RetryWhenMainSubscriber");
    } catch (ClassNotFoundException ignored) {
      return null;
    }
  }
}
