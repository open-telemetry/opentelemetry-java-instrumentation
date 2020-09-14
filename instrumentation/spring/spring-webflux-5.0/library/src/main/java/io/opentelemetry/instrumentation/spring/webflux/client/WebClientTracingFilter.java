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

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
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
      Span span = TRACER.startSpan(request);
      ClientRequest.Builder builder = ClientRequest.from(request);
      try (Scope ignored = TRACER.startScope(span, builder)) {
        this.next
            .exchange(builder.build())
            .doOnCancel(
                () -> {
                  TRACER.onCancel(span);
                  TRACER.end(span);
                })
            .subscribe(new TraceWebClientSubscriber(subscriber, span, io.grpc.Context.current()));
      }
    }
  }
}
