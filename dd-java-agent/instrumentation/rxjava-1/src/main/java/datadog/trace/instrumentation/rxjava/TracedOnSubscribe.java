package datadog.trace.instrumentation.rxjava;

import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.activeScope;
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
  private final AgentScope.Continuation continuation;
  private final BaseDecorator decorator;

  public TracedOnSubscribe(
      final Observable originalObservable,
      final String operationName,
      final BaseDecorator decorator) {
    delegate = DDTracingUtil.extractOnSubscribe(originalObservable);
    this.operationName = operationName;
    this.decorator = decorator;

    continuation = activeScope().capture();
  }

  @Override
  public void call(final Subscriber<? super T> subscriber) {
    final AgentSpan span; // span finished by TracedSubscriber
    if (continuation != null) {
      try (final AgentScope scope = continuation.activate()) {
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
