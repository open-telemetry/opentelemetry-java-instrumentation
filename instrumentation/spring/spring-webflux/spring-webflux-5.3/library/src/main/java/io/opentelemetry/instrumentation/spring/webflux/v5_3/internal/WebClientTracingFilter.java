/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;

/**
 * Based on Spring Sleuth's Reactor instrumentation.
 * https://github.com/spring-cloud/spring-cloud-sleuth/blob/master/spring-cloud-sleuth-core/src/main/java/org/springframework/cloud/sleuth/instrument/web/client/TraceWebClientBeanPostProcessor.java
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class WebClientTracingFilter implements ExchangeFilterFunction {

  private final Instrumenter<ClientRequest, ClientResponse> instrumenter;
  private final ContextPropagators propagators;

  public WebClientTracingFilter(
      Instrumenter<ClientRequest, ClientResponse> instrumenter, ContextPropagators propagators) {
    this.instrumenter = instrumenter;
    this.propagators = propagators;
  }

  @Override
  public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
    return new MonoWebClientTrace(request, next);
  }

  private final class MonoWebClientTrace extends Mono<ClientResponse> {

    private final ExchangeFunction next;
    private final ClientRequest request;

    private MonoWebClientTrace(ClientRequest request, ExchangeFunction next) {
      this.next = next;
      this.request = request;
    }

    @Override
    public void subscribe(CoreSubscriber<? super ClientResponse> subscriber) {
      Context parentContext = Context.current();
      if (!instrumenter.shouldStart(parentContext, request)) {
        next.exchange(request).subscribe(subscriber);
        return;
      }

      Context context = instrumenter.start(parentContext, request);

      ClientRequest.Builder builder = ClientRequest.from(request);
      propagators.getTextMapPropagator().inject(context, builder, HttpHeadersSetter.INSTANCE);

      ClientRequest injectedRequest = builder.build();
      Mono<ClientResponse> response =
          Mono.defer(() -> next.exchange(injectedRequest))
              .doOnCancel(
                  // no response and no error means that the request has been cancelled
                  () -> instrumenter.end(context, request, null, null));
      ContextPropagationOperator.runWithContext(response, context, parentContext)
          .doOnNext(result -> instrumenter.end(context, request, result, null))
          .doOnError(error -> instrumenter.end(context, request, null, error))
          .subscribe(subscriber);
    }
  }
}
