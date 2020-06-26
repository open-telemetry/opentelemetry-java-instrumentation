/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.reactor;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.core.Scannable;
import reactor.util.context.Context;

@Slf4j
public class TracingSubscriber<T>
    implements Subscription, CoreSubscriber<T>, Fuseable.QueueSubscription<T>, Scannable {

  private static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.reactor");

  private final AtomicReference<Span> continuation = new AtomicReference<>();

  private final Span upstreamSpan;
  private final CoreSubscriber<T> delegate;
  private final Context context;
  private final Span downstreamSpan;
  private Subscription subscription;

  public TracingSubscriber(final Span upstreamSpan, final CoreSubscriber<T> delegate) {
    this.delegate = delegate;
    this.upstreamSpan = upstreamSpan;
    downstreamSpan =
        (Span) delegate.currentContext().getOrEmpty(Span.class).orElseGet(DefaultSpan::getInvalid);

    // The context is exposed upstream so we put our upstream span here for use by the next
    // TracingSubscriber
    context = this.delegate.currentContext().put(Span.class, this.upstreamSpan);
  }

  @Override
  public Context currentContext() {
    return context;
  }

  @Override
  public void onSubscribe(final Subscription subscription) {
    this.subscription = subscription;

    try (final Scope scope = TRACER.withSpan(downstreamSpan)) {
      delegate.onSubscribe(this);
    }
  }

  @Override
  public void onNext(final T t) {
    try (final Scope scope = TRACER.withSpan(downstreamSpan)) {
      delegate.onNext(t);
    }
  }

  private Scope finalScopeForDownstream() {
    Span span = continuation.getAndSet(null);
    if (span != null) {
      return TRACER.withSpan(span);
    } else {
      return TRACER.withSpan(downstreamSpan);
    }
  }

  @Override
  public void onError(final Throwable t) {
    try (final Scope scope = finalScopeForDownstream()) {
      delegate.onError(t);
    }
  }

  @Override
  public void onComplete() {
    try (final Scope scope = finalScopeForDownstream()) {
      delegate.onComplete();
    }
  }

  /*
   * Methods from Subscription
   */

  @Override
  public void request(final long n) {
    try (final Scope scope = TRACER.withSpan(upstreamSpan)) {
      subscription.request(n);
    }
  }

  @Override
  public void cancel() {
    try (final Scope scope = TRACER.withSpan(upstreamSpan)) {
      subscription.cancel();
    }
  }

  /*
   * Methods from Scannable
   */

  @Override
  public Object scanUnsafe(final Attr attr) {
    if (attr == Attr.PARENT) {
      return subscription;
    }
    if (attr == Attr.ACTUAL) {
      return delegate;
    }
    return null;
  }

  /*
   * Methods from Fuseable.QueueSubscription
   */

  @Override
  public int requestFusion(final int requestedMode) {
    return Fuseable.NONE;
  }

  @Override
  public T poll() {
    return null;
  }

  @Override
  public int size() {
    return 0;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public void clear() {}
}
