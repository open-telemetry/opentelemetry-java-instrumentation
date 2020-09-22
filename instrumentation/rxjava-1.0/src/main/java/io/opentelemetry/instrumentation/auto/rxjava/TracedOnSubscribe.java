/*
 * Copyright The OpenTelemetry Authors
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

package io.opentelemetry.instrumentation.auto.rxjava;

import io.grpc.Context;
import io.opentelemetry.context.ContextUtils;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.trace.Span;
import rx.Observable;
import rx.Subscriber;
import rx.__OpenTelemetryTracingUtil;

public class TracedOnSubscribe<T> implements Observable.OnSubscribe<T> {
  protected final Observable.OnSubscribe<?> delegate;
  protected final String operationName;
  protected final Context parentContext;
  protected final BaseTracer tracer;
  protected final Span.Kind spanKind;

  public TracedOnSubscribe(
      Observable originalObservable, String operationName, BaseTracer tracer, Span.Kind spanKind) {
    delegate = __OpenTelemetryTracingUtil.extractOnSubscribe(originalObservable);
    this.operationName = operationName;
    this.tracer = tracer;
    this.spanKind = spanKind;

    parentContext = Context.current();
  }

  @Override
  public void call(Subscriber<? super T> subscriber) {
    try (Scope ignored = ContextUtils.withScopedContext(parentContext)) {
      Span span = tracer.startSpan(operationName, spanKind);
      decorateSpan(span);
      try (Scope ignored1 = tracer.startScope(span)) {
        delegate.call(new TracedSubscriber(span, subscriber, tracer));
      }
    }
  }

  protected void decorateSpan(Span span) {
    // Subclasses can use it to provide addition attributes to the span
  }
}
