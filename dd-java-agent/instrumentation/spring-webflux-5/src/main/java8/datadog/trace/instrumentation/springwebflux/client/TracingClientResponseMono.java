package datadog.trace.instrumentation.springwebflux.client;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.springwebflux.client.HttpHeadersInjectAdapter.SETTER;
import static datadog.trace.instrumentation.springwebflux.client.SpringWebfluxHttpClientDecorator.DECORATE;

import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentTracer;
import datadog.trace.bootstrap.instrumentation.api.Tags;
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
    final AgentSpan parentSpan =
        context.<AgentSpan>getOrEmpty(AgentSpan.class).orElseGet(AgentTracer::activeSpan);

    final AgentSpan span;
    if (parentSpan != null) {
      span = startSpan("http.request", parentSpan.context());
    } else {
      span = startSpan("http.request");
    }
    span.setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT);
    DECORATE.afterStart(span);

    try (final AgentScope scope = activateSpan(span, false)) {

      scope.setAsyncPropagation(true);

      final ClientRequest mutatedRequest =
          ClientRequest.from(clientRequest)
              .headers(httpHeaders -> propagate().inject(span, httpHeaders, SETTER))
              .build();
      exchangeFunction
          .exchange(mutatedRequest)
          .subscribe(
              new TracingClientResponseSubscriber(
                  subscriber, mutatedRequest, context, span, parentSpan));
    }
  }
}
