package io.opentelemetry.auto.instrumentation.springwebflux.client;

import static io.opentelemetry.auto.instrumentation.springwebflux.client.HttpHeadersInjectAdapter.SETTER;
import static io.opentelemetry.auto.instrumentation.springwebflux.client.SpringWebfluxHttpClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.springwebflux.client.SpringWebfluxHttpClientDecorator.TRACER;

import io.opentelemetry.auto.instrumentation.api.Tags;
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

    final Span.Builder builder = TRACER.spanBuilder("http.request");
    if (parentSpan != null) {
      builder.setParent(parentSpan);
    } else {
      builder.setNoParent();
    }
    final Span span = builder.startSpan();
    span.setAttribute(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT);
    DECORATE.afterStart(span);

    try (final Scope scope = TRACER.withSpan(span)) {

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
