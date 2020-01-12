package io.opentelemetry.auto.instrumentation.rxjava;

import io.opentelemetry.auto.agent.decorator.BaseDecorator;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import java.util.concurrent.atomic.AtomicReference;
import rx.Subscription;

public class SpanFinishingSubscription implements Subscription {
  private final BaseDecorator decorator;
  private final AtomicReference<AgentSpan> spanRef;

  public SpanFinishingSubscription(
      final BaseDecorator decorator, final AtomicReference<AgentSpan> spanRef) {
    this.decorator = decorator;
    this.spanRef = spanRef;
  }

  @Override
  public void unsubscribe() {
    final AgentSpan span = spanRef.getAndSet(null);
    if (span != null) {
      decorator.beforeFinish(span);
      span.finish();
    }
  }

  @Override
  public boolean isUnsubscribed() {
    return spanRef.get() == null;
  }
}
