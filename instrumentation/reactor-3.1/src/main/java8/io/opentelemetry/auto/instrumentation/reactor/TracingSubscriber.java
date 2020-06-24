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
import io.opentelemetry.context.ContextUtils;
import io.opentelemetry.context.Scope;
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

  private final io.grpc.Context upstreamContext;
  private final CoreSubscriber<T> delegate;
  private final Context context;
  private final io.grpc.Context downstreamContext;
  private Subscription subscription;

  public TracingSubscriber(final io.grpc.Context upstreamContext,
      final CoreSubscriber<T> delegate) {
    this.delegate = delegate;
    this.upstreamContext = upstreamContext;
    this.downstreamContext =
        (io.grpc.Context) delegate.currentContext().getOrEmpty(io.grpc.Context.class)
            .orElse(io.grpc.Context.ROOT);

    // The context is exposed upstream so we put our upstream context here for use by the next
    // TracingSubscriber
    context = this.delegate.currentContext().put(io.grpc.Context.class, this.upstreamContext);
  }

  @Override
  public Context currentContext() {
    return context;
  }

  @Override
  public void onSubscribe(final Subscription subscription) {
    this.subscription = subscription;

    try (final Scope scope = ContextUtils.withScopedContext(downstreamContext)) {
      delegate.onSubscribe(this);
    }
  }

  @Override
  public void onNext(final T t) {
    try (final Scope scope = ContextUtils.withScopedContext(downstreamContext)) {
      delegate.onNext(t);
    }
  }

  private Scope finalScopeForDownstream() {
    return ContextUtils.withScopedContext(downstreamContext);
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
    try (final Scope scope = ContextUtils.withScopedContext(upstreamContext)) {
      subscription.request(n);
    }
  }

  @Override
  public void cancel() {
    try (final Scope scope = ContextUtils.withScopedContext(upstreamContext)) {
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
