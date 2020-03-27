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

import static io.opentelemetry.auto.instrumentation.springwebflux.client.HttpHeadersInjectAdapter.SETTER;
import static io.opentelemetry.auto.instrumentation.springwebflux.client.SpringWebfluxHttpClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.springwebflux.client.SpringWebfluxHttpClientDecorator.TRACER;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public class TracingClientResponseMono extends Mono<ClientResponse> {

  private final ClientRequest clientRequest;
  private final ExchangeFunction exchangeFunction;

  public TracingClientResponseMono(
      final ClientRequest clientRequest, final ExchangeFunction exchangeFunction) {
    this.clientRequest = clientRequest;
    this.exchangeFunction = exchangeFunction;
  }

  @Override
  public void subscribe(final CoreSubscriber<? super ClientResponse> subscriber) {
    final Context context = subscriber.currentContext();
    final Span parentSpan = context.<Span>getOrEmpty(Span.class).orElseGet(TRACER::getCurrentSpan);

    final Span.Builder builder =
        TRACER.spanBuilder(DECORATE.spanNameForRequest(clientRequest)).setSpanKind(CLIENT);
    if (parentSpan != null) {
      builder.setParent(parentSpan);
    } else {
    }
    final Span span = builder.startSpan();
    DECORATE.afterStart(span);

    try (final Scope scope = currentContextWith(span)) {

      final ClientRequest mutatedRequest =
          ClientRequest.from(clientRequest)
              .headers(
                  httpHeaders ->
                      TRACER.getHttpTextFormat().inject(span.getContext(), httpHeaders, SETTER))
              .build();
      exchangeFunction
          .exchange(mutatedRequest)
          .subscribe(
              new TracingClientResponseSubscriber(
                  subscriber, mutatedRequest, context, span, parentSpan));
    }
  }
}
