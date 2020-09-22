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

package io.opentelemetry.instrumentation.auto.rxjava;

import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.trace.Span;
import java.util.concurrent.atomic.AtomicReference;
import rx.Subscriber;

public class TracedSubscriber<T> extends Subscriber<T> {

  private final AtomicReference<Span> spanRef;
  private final Subscriber<T> delegate;
  private final BaseTracer tracer;

  // TODO pass the whole context here, not just span
  public TracedSubscriber(Span span, Subscriber<T> delegate, BaseTracer tracer) {
    spanRef = new AtomicReference<>(span);
    this.delegate = delegate;
    this.tracer = tracer;
    SpanFinishingSubscription subscription = new SpanFinishingSubscription(tracer, spanRef);
    delegate.add(subscription);
  }

  @Override
  public void onStart() {
    Span span = spanRef.get();
    if (span != null) {
      try (Scope ignored = currentContextWith(span)) {
        delegate.onStart();
      }
    } else {
      delegate.onStart();
    }
  }

  @Override
  public void onNext(T value) {
    Span span = spanRef.get();
    if (span != null) {
      try (Scope ignored = currentContextWith(span)) {
        delegate.onNext(value);
      } catch (Throwable e) {
        onError(e);
      }
    } else {
      delegate.onNext(value);
    }
  }

  @Override
  public void onCompleted() {
    Span span = spanRef.getAndSet(null);
    if (span != null) {
      boolean errored = false;
      try (Scope ignored = currentContextWith(span)) {
        delegate.onCompleted();
      } catch (Throwable e) {
        // Repopulate the spanRef for onError
        spanRef.compareAndSet(null, span);
        onError(e);
        errored = true;
      } finally {
        // finish called by onError, so don't finish again.
        if (!errored) {
          tracer.end(span);
        }
      }
    } else {
      delegate.onCompleted();
    }
  }

  @Override
  public void onError(Throwable e) {
    Span span = spanRef.getAndSet(null);
    if (span != null) {
      try (Scope ignored = currentContextWith(span)) {
        delegate.onError(e);
        tracer.endExceptionally(span, e);
      } catch (Throwable e2) {
        tracer.endExceptionally(span, e2);
        throw e2;
      }
    } else {
      delegate.onError(e);
    }
  }
}
