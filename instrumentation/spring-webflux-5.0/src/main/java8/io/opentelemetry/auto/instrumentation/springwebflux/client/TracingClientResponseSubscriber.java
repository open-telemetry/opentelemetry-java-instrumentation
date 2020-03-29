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
package io.opentelemetry.auto.instrumentation.springwebflux.client;

import static io.opentelemetry.auto.instrumentation.springwebflux.client.SpringWebfluxHttpClientDecorator.DECORATE;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;
import java.util.concurrent.atomic.AtomicReference;
import org.reactivestreams.Subscription;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

public class TracingClientResponseSubscriber implements CoreSubscriber<ClientResponse> {

  private final CoreSubscriber<? super ClientResponse> subscriber;
  private final ClientRequest clientRequest;
  private final Context context;
  private final AtomicReference<Span> spanRef;
  private final Span parentSpan;

  public TracingClientResponseSubscriber(
      final CoreSubscriber<? super ClientResponse> subscriber,
      final ClientRequest clientRequest,
      final Context context,
      final Span span,
      final Span parentSpan) {
    this.subscriber = subscriber;
    this.clientRequest = clientRequest;
    this.context = context;
    spanRef = new AtomicReference<>(span);
    this.parentSpan = parentSpan == null ? DefaultSpan.getInvalid() : parentSpan;
  }

  @Override
  public void onSubscribe(final Subscription subscription) {
    final Span span = spanRef.get();
    if (span == null) {
      subscriber.onSubscribe(subscription);
      return;
    }

    try (final Scope scope = currentContextWith(span)) {

      DECORATE.onRequest(span, clientRequest);

      subscriber.onSubscribe(
          new Subscription() {
            @Override
            public void request(final long n) {
              try (final Scope scope = currentContextWith(span)) {
                subscription.request(n);
              }
            }

            @Override
            public void cancel() {
              DECORATE.onCancel(span);
              DECORATE.beforeFinish(span);
              subscription.cancel();
              span.end();
            }
          });
    }
  }

  @Override
  public void onNext(final ClientResponse clientResponse) {
    final Span span = spanRef.getAndSet(null);
    if (span != null) {
      DECORATE.onResponse(span, clientResponse);
      DECORATE.beforeFinish(span);
      span.end();
    }

    try (final Scope scope = currentContextWith(parentSpan)) {
      subscriber.onNext(clientResponse);
    }
  }

  @Override
  public void onError(final Throwable throwable) {
    final Span span = spanRef.getAndSet(null);
    if (span != null) {
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.end();
    }

    try (final Scope scope = currentContextWith(parentSpan)) {

      subscriber.onError(throwable);
    }
  }

  @Override
  public void onComplete() {
    final Span span = spanRef.getAndSet(null);
    if (span != null) {
      DECORATE.beforeFinish(span);
      span.end();
    }

    try (final Scope scope = currentContextWith(parentSpan)) {

      subscriber.onComplete();
    }
  }

  @Override
  public Context currentContext() {
    return context;
  }
}
