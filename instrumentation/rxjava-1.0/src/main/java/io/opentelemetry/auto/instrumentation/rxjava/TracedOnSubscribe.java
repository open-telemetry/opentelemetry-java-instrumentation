/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.rxjava;

import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.BaseDecorator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import rx.Observable;
import rx.Subscriber;
import rx.__OpenTelemetryTracingUtil;

public class TracedOnSubscribe<T> implements Observable.OnSubscribe<T> {
  private static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.rxjava-1.0");

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

    try (final Scope scope = currentContextWith(span)) {
      delegate.call(new TracedSubscriber(span, subscriber, decorator));
    }
  }

  protected void afterStart(final Span span) {
    decorator.afterStart(span);
  }
}
