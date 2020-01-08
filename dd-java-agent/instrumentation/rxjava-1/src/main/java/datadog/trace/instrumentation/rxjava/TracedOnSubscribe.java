package datadog.trace.instrumentation.rxjava;

import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;

import datadog.trace.agent.decorator.BaseDecorator;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
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
