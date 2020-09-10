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

package io.opentelemetry.instrumentation.spring.webflux.client;

import static io.opentelemetry.instrumentation.spring.webflux.client.SpringWebfluxHttpClientTracer.TRACER;

import io.opentelemetry.context.ContextUtils;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import org.reactivestreams.Subscription;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.CoreSubscriber;

/**
 * @author Based on Spring Sleuth's Reactor instrumentation.
 * @author Marcin Grzejszczak
 */
public final class TraceWebClientSubscriber implements CoreSubscriber<ClientResponse> {

  final CoreSubscriber<? super ClientResponse> actual;

  final reactor.util.context.Context context;

  private final Span span;
  private final io.grpc.Context tracingContext;

  public TraceWebClientSubscriber(
      CoreSubscriber<? super ClientResponse> actual, Span span, io.grpc.Context tracingContext) {
    this.actual = actual;
    this.span = span;
    this.tracingContext = tracingContext;
    this.context = actual.currentContext();
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    this.actual.onSubscribe(subscription);
  }

  @Override
  public void onNext(ClientResponse response) {
    try (Scope ignored = ContextUtils.withScopedContext(tracingContext)) {
      this.actual.onNext(response);
    } finally {
      TRACER.end(span, response);
    }
  }

  @Override
  public void onError(Throwable t) {
    try (Scope ignored = ContextUtils.withScopedContext(tracingContext)) {
      this.actual.onError(t);
    } finally {
      TRACER.endExceptionally(span, t);
    }
  }

  @Override
  public void onComplete() {
    try (Scope ignored = ContextUtils.withScopedContext(tracingContext)) {
      this.actual.onComplete();
    }
  }

  @Override
  public reactor.util.context.Context currentContext() {
    return this.context;
  }
}
