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

package io.opentelemetry.auto.instrumentation.rxjava;

import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.auto.bootstrap.instrumentation.decorator.BaseDecorator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.util.concurrent.atomic.AtomicReference;
import rx.Subscriber;

public class TracedSubscriber<T> extends Subscriber<T> {

  private final AtomicReference<Span> spanRef;
  private final Subscriber<T> delegate;
  private final BaseDecorator decorator;

  public TracedSubscriber(
      final Span span, final Subscriber<T> delegate, final BaseDecorator decorator) {
    spanRef = new AtomicReference<>(span);
    this.delegate = delegate;
    this.decorator = decorator;
    final SpanFinishingSubscription subscription =
        new SpanFinishingSubscription(decorator, spanRef);
    delegate.add(subscription);
  }

  @Override
  public void onStart() {
    final Span span = spanRef.get();
    if (span != null) {
      try (final Scope scope = currentContextWith(span)) {
        delegate.onStart();
      }
    } else {
      delegate.onStart();
    }
  }

  @Override
  public void onNext(final T value) {
    final Span span = spanRef.get();
    if (span != null) {
      try (final Scope scope = currentContextWith(span)) {
        delegate.onNext(value);
      } catch (final Throwable e) {
        onError(e);
      }
    } else {
      delegate.onNext(value);
    }
  }

  @Override
  public void onCompleted() {
    final Span span = spanRef.getAndSet(null);
    if (span != null) {
      boolean errored = false;
      try (final Scope scope = currentContextWith(span)) {
        delegate.onCompleted();
      } catch (final Throwable e) {
        // Repopulate the spanRef for onError
        spanRef.compareAndSet(null, span);
        onError(e);
        errored = true;
      } finally {
        // finish called by onError, so don't finish again.
        if (!errored) {
          decorator.beforeFinish(span);
          span.end();
        }
      }
    } else {
      delegate.onCompleted();
    }
  }

  @Override
  public void onError(final Throwable e) {
    final Span span = spanRef.getAndSet(null);
    if (span != null) {
      try (final Scope scope = currentContextWith(span)) {
        decorator.onError(span, e);
        delegate.onError(e);
      } catch (final Throwable e2) {
        decorator.onError(span, e2);
        throw e2;
      } finally {
        decorator.beforeFinish(span);
        span.end();
      }
    } else {
      delegate.onError(e);
    }
  }
}
