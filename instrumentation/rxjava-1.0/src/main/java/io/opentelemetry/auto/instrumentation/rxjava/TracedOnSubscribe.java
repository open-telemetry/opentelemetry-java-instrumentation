package io.opentelemetry.auto.instrumentation.rxjava;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.BaseDecorator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import rx.Observable;
import rx.Subscriber;
import rx.__OpenTelemetryTracingUtil;

public class TracedOnSubscribe<T> implements Observable.OnSubscribe<T> {
  private static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.rxjava-1.0");

  private final Observable.OnSubscribe<?> delegate;
  private final String operationName;
  private final Span parentSpan;
  private final BaseDecorator decorator;
  private final Span.Kind spanKind;

  public TracedOnSubscribe(
      final Observable originalObservable,
      final String operationName,
      final BaseDecorator decorator,
      final Span.Kind spanKind) {
    delegate = __OpenTelemetryTracingUtil.extractOnSubscribe(originalObservable);
    this.operationName = operationName;
    this.decorator = decorator;
    this.spanKind = spanKind;

    parentSpan = TRACER.getCurrentSpan();
  }

  @Override
  public void call(final Subscriber<? super T> subscriber) {
    // span finished by TracedSubscriber
    final Span.Builder spanBuilder = TRACER.spanBuilder(operationName).setSpanKind(spanKind);
    if (parentSpan != null) {
      spanBuilder.setParent(parentSpan);
    }
    final Span span = spanBuilder.startSpan();

    afterStart(span);

    try (final Scope scope = TRACER.withSpan(span)) {
      delegate.call(new TracedSubscriber(span, subscriber, decorator));
    }
  }

  protected void afterStart(final Span span) {
    decorator.afterStart(span);
  }
}
