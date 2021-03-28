/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.client;

import static io.opentelemetry.instrumentation.spring.webflux.client.SpringWebfluxHttpClientTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.List;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;

/**
 * Based on Spring Sleuth's Reactor instrumentation.
 * https://github.com/spring-cloud/spring-cloud-sleuth/blob/master/spring-cloud-sleuth-core/src/main/java/org/springframework/cloud/sleuth/instrument/web/client/TraceWebClientBeanPostProcessor.java
 */
public class WebClientTracingFilter implements ExchangeFilterFunction {

  public static void addFilter(List<ExchangeFilterFunction> exchangeFilterFunctions) {
    for (ExchangeFilterFunction filterFunction : exchangeFilterFunctions) {
      if (filterFunction instanceof WebClientTracingFilter) {
        return;
      }
    }
    exchangeFilterFunctions.add(0, new WebClientTracingFilter());
  }

  @Override
  public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
    return new MonoWebClientTrace(request, next);
  }

  public static final class MonoWebClientTrace extends Mono<ClientResponse> {

    final ExchangeFunction next;
    final ClientRequest request;

    public MonoWebClientTrace(ClientRequest request, ExchangeFunction next) {
      this.next = next;
      this.request = request;
    }

    @Override
    public void subscribe(CoreSubscriber<? super ClientResponse> subscriber) {
      Context parentContext = Context.current();
      if (!tracer().shouldStartSpan(parentContext)) {
        next.exchange(request).subscribe(subscriber);
        return;
      }

      ClientRequest.Builder builder = ClientRequest.from(request);
      Context context = tracer().startSpan(parentContext, request, builder);
      try (Scope ignored = context.makeCurrent()) {
        this.next
            .exchange(builder.build())
            .doOnCancel(
                () -> {
                  tracer().onCancel(context);
                  tracer().end(context);
                })
            .subscribe(new TraceWebClientSubscriber(subscriber, context));
      }
    }
  }
}
