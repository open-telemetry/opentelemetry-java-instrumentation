package io.opentelemetry.auto.instrumentation.rxjava;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.BaseDecorator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.concurrent.atomic.AtomicReference;
import rx.Subscriber;

public class TracedSubscriber<T> extends Subscriber<T> {
  private static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

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
      try (final Scope scope = TRACER.withSpan(span)) {
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
      try (final Scope scope = TRACER.withSpan(span)) {
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
      try (final Scope scope = TRACER.withSpan(span)) {
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
      try (final Scope scope = TRACER.withSpan(span)) {
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
