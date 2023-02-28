/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.reactivestreams.Subscription;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.util.annotation.NonNull;

final class TelemetryProducingWebFilter implements WebFilter, Ordered {

  private final Instrumenter<ServerWebExchange, ServerWebExchange> instrumenter;

  TelemetryProducingWebFilter(Instrumenter<ServerWebExchange, ServerWebExchange> instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  @NonNull
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    Context parentContext = Context.current();
    Mono<Void> source = chain.filter(exchange);
    return new TelemetryWrappedMono(source, instrumenter, parentContext, exchange);
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 1;
  }

  private static class TelemetryWrappedMono extends Mono<Void> {

    private final Mono<Void> source;
    private final Instrumenter<ServerWebExchange, ServerWebExchange> instrumenter;
    private final Context parentContext;
    private final ServerWebExchange exchange;

    TelemetryWrappedMono(
        Mono<Void> source,
        Instrumenter<ServerWebExchange, ServerWebExchange> instrumenter,
        Context parentContext,
        ServerWebExchange exchange) {
      this.source = source;
      this.instrumenter = instrumenter;
      this.parentContext = parentContext;
      this.exchange = exchange;
    }

    @Override
    public void subscribe(CoreSubscriber<? super Void> actual) {
      if (!instrumenter.shouldStart(parentContext, exchange)) {
        source.subscribe(actual);
        return;
      }
      Context currentContext = instrumenter.start(parentContext, exchange);
      try (Scope ignored = currentContext.makeCurrent()) {
        this.source.subscribe(
            new TelemetryWrappedSubscriber(actual, currentContext, instrumenter, exchange));
      }
    }
  }

  private static class TelemetryWrappedSubscriber implements CoreSubscriber<Void> {

    private static final HttpServerAttributesGetter<ServerWebExchange, ServerWebExchange>
        attributesGetter = WebfluxServerHttpAttributesGetter.INSTANCE;
    private static final SpanNameExtractor<ServerWebExchange> spanNameExtractor =
        HttpSpanNameExtractor.create(WebfluxServerHttpAttributesGetter.INSTANCE);

    private final CoreSubscriber<? super Void> actual;
    private final Instrumenter<ServerWebExchange, ServerWebExchange> instrumenter;
    private final Context currentOtelContext;
    private final ServerWebExchange exchange;

    TelemetryWrappedSubscriber(
        CoreSubscriber<? super Void> actual,
        Context currentOtelContext,
        Instrumenter<ServerWebExchange, ServerWebExchange> instrumenter,
        ServerWebExchange exchange) {
      this.actual = actual;
      this.instrumenter = instrumenter;
      this.currentOtelContext = currentOtelContext;
      this.exchange = exchange;
    }

    @Override
    public reactor.util.context.Context currentContext() {
      return actual.currentContext();
    }

    @Override
    public void onSubscribe(Subscription s) {
      actual.onSubscribe(s);
    }

    @Override
    public void onNext(Void unused) {}

    @Override
    public void onError(Throwable t) {
      onTerminal(currentOtelContext, t);
      actual.onError(t);
    }

    @Override
    public void onComplete() {
      onTerminal(currentOtelContext, null);
      actual.onComplete();
    }

    private void onTerminal(Context currentContext, Throwable t) {
      ServerHttpResponse response = exchange.getResponse();
      if (response.isCommitted()) {
        end(currentContext, t);
      } else {
        response.beforeCommit(
            () -> {
              end(currentContext, t);
              return Mono.empty();
            });
      }
    }

    private void end(Context currentContext, Throwable t) {
      // Update span name and HTTP route now, because during instrumenter.start()
      // the HTTP route isn't available from the exchange attributes, but is afterwards
      String spanName = spanNameExtractor.extract(exchange);
      String route = attributesGetter.getRoute(exchange);
      Span span = Span.fromContext(currentContext);
      span.updateName(spanName);
      if (route != null) {
        span.setAttribute(SemanticAttributes.HTTP_ROUTE, route);
      }
      instrumenter.end(currentContext, exchange, exchange, t);
    }
  }
}
