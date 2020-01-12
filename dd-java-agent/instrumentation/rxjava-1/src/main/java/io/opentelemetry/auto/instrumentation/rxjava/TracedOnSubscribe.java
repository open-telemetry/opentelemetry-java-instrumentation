package io.opentelemetry.auto.instrumentation.rxjava;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activeSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan;

import io.opentelemetry.auto.agent.decorator.BaseDecorator;
import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import rx.DDTracingUtil;
import rx.Observable;
import rx.Subscriber;

public class TracedOnSubscribe<T> implements Observable.OnSubscribe<T> {

  private final Observable.OnSubscribe<?> delegate;
  private final String operationName;
  private final AgentSpan parentSpan;
  private final BaseDecorator decorator;

  public TracedOnSubscribe(
      final Observable originalObservable,
      final String operationName,
      final BaseDecorator decorator) {
    delegate = DDTracingUtil.extractOnSubscribe(originalObservable);
    this.operationName = operationName;
    this.decorator = decorator;

    parentSpan = activeSpan();
  }

  @Override
  public void call(final Subscriber<? super T> subscriber) {
    final AgentSpan span; // span finished by TracedSubscriber
    if (parentSpan != null) {
      try (final AgentScope scope = activateSpan(parentSpan, false)) {
        span = startSpan(operationName);
      }
    } else {
      span = startSpan(operationName);
    }

    afterStart(span);

    try (final AgentScope scope = activateSpan(span, false)) {
      delegate.call(new TracedSubscriber(span, subscriber, decorator));
    }
  }

  protected void afterStart(final AgentSpan span) {
    decorator.afterStart(span);
  }
}
