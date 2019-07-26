package datadog.trace.instrumentation.springwebflux.client;

import static datadog.trace.instrumentation.springwebflux.client.SpringWebfluxHttpClientDecorator.DECORATE;

import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public class TracingClientResponseMono extends Mono<ClientResponse> {

  private final ClientRequest clientRequest;
  private final ExchangeFunction exchangeFunction;
  private final Tracer tracer;

  public TracingClientResponseMono(
      final ClientRequest clientRequest,
      final ExchangeFunction exchangeFunction,
      final Tracer tracer) {
    this.clientRequest = clientRequest;
    this.exchangeFunction = exchangeFunction;
    this.tracer = tracer;
  }

  @Override
  public void subscribe(final CoreSubscriber<? super ClientResponse> subscriber) {
    final Context context = subscriber.currentContext();
    final Span parentSpan = context.<Span>getOrEmpty(Span.class).orElseGet(tracer::activeSpan);

    final Span span =
        tracer
            .buildSpan("http.request")
            .asChildOf(parentSpan)
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
            .start();
    DECORATE.afterStart(span);

    try (final Scope scope = tracer.scopeManager().activate(span, false)) {

      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(true);
      }

      final ClientRequest mutatedRequest =
          ClientRequest.from(clientRequest)
              .headers(
                  httpHeaders ->
                      tracer.inject(
                          span.context(),
                          Format.Builtin.HTTP_HEADERS,
                          new HttpHeadersInjectAdapter(httpHeaders)))
              .build();
      exchangeFunction
          .exchange(mutatedRequest)
          .subscribe(
              new TracingClientResponseSubscriber(subscriber, mutatedRequest, context, span, parentSpan));
    }
  }
}
