package datadog.trace.instrumentation.rxjava;

import datadog.trace.agent.decorator.BaseDecorator;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
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
    this.delegate = DDTracingUtil.extractOnSubscribe(originalObservable);
    this.operationName = operationName;
    this.decorator = decorator;

    final Scope parentScope = GlobalTracer.get().scopeManager().active();

    continuation = parentScope instanceof TraceScope ? ((TraceScope) parentScope).capture() : null;
  }

  @Override
  public void call(final Subscriber<? super T> subscriber) {
    final Tracer tracer = GlobalTracer.get();
    final Span span; // span finished by TracedSubscriber
    if (continuation != null) {
      try (final TraceScope scope = continuation.activate()) {
        span = tracer.buildSpan(operationName).start();
      }
    } else {
      span = tracer.buildSpan(operationName).start();
    }

    afterStart(span);

    try (final Scope scope = tracer.scopeManager().activate(span, false)) {
      if (!((TraceScope) scope).isAsyncPropagating()) {
        ((TraceScope) scope).setAsyncPropagation(true);
      }
      delegate.call(new TracedSubscriber(span, subscriber, decorator));
    }
  }

  protected void afterStart(final Span span) {
    decorator.afterStart(span);
  }
}
