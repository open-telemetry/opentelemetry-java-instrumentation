package datadog.trace.instrumentation.rxjava;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;

import datadog.trace.agent.decorator.BaseDecorator;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.context.TraceScope;
import rx.DDTracingUtil;
import rx.Observable;
import rx.Subscriber;

public class TracedOnSubscribe<T> implements Observable.OnSubscribe<T> {

  private final Observable.OnSubscribe<?> delegate;
  private final String operationName;
  private final TraceScope.Continuation continuation;
  private final BaseDecorator decorator;

  public TracedOnSubscribe(
      final Observable originalObservable,
      final String operationName,
      final BaseDecorator decorator) {
    delegate = DDTracingUtil.extractOnSubscribe(originalObservable);
    this.operationName = operationName;
    this.decorator = decorator;

    continuation = propagate().capture();
  }

  @Override
  public void call(final Subscriber<? super T> subscriber) {
    final AgentSpan span; // span finished by TracedSubscriber
    if (continuation != null) {
      try (final TraceScope scope = continuation.activate()) {
        span = startSpan(operationName);
      }
    } else {
      span = startSpan(operationName);
    }

    afterStart(span);

    try (final AgentScope scope = activateSpan(span, false)) {
      scope.setAsyncPropagation(true);
      delegate.call(new TracedSubscriber(span, subscriber, decorator));
    }
  }

  protected void afterStart(final AgentSpan span) {
    decorator.afterStart(span);
  }
}
