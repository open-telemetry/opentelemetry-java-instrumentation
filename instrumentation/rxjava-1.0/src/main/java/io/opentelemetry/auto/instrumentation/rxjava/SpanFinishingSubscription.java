package io.opentelemetry.auto.instrumentation.rxjava;

import io.opentelemetry.auto.decorator.BaseDecorator;
import io.opentelemetry.trace.Span;
import java.util.concurrent.atomic.AtomicReference;
import rx.Subscription;

public class SpanFinishingSubscription implements Subscription {
  private final BaseDecorator decorator;
  private final AtomicReference<Span> spanRef;

  public SpanFinishingSubscription(
      final BaseDecorator decorator, final AtomicReference<Span> spanRef) {
    this.decorator = decorator;
    this.spanRef = spanRef;
  }

  @Override
  public void unsubscribe() {
    final Span span = spanRef.getAndSet(null);
    if (span != null) {
      decorator.beforeFinish(span);
      span.end();
    }
  }

  @Override
  public boolean isUnsubscribed() {
    return spanRef.get() == null;
  }
}
