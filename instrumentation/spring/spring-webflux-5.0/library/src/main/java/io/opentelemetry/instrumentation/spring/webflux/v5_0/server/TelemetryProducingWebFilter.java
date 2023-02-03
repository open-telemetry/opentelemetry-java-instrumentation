/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_0.server;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.reactivestreams.Subscription;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.util.annotation.NonNull;

final class TelemetryProducingWebFilter implements WebFilter {

  private final Instrumenter<ServerHttpRequest, ServerHttpResponse> instrumenter;

  TelemetryProducingWebFilter(Instrumenter<ServerHttpRequest, ServerHttpResponse> instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  @NonNull
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    Context parentContext = Context.current();
    Mono<Void> source = chain.filter(exchange);
    return new TelemetryWrappedMono(source, instrumenter, parentContext, exchange);
  }

  private static class TelemetryWrappedMono extends Mono<Void> {

    private final Mono<Void> source;
    private final Instrumenter<ServerHttpRequest, ServerHttpResponse> instrumenter;
    private final Context parentContext;
    private final ServerWebExchange exchange;

    TelemetryWrappedMono(
        Mono<Void> source,
        Instrumenter<ServerHttpRequest, ServerHttpResponse> instrumenter,
        Context parentContext,
        ServerWebExchange exchange) {
      this.source = source;
      this.instrumenter = instrumenter;
      this.parentContext = parentContext;
      this.exchange = exchange;
    }

    @Override
    public void subscribe(CoreSubscriber<? super Void> actual) {
      if (!instrumenter.shouldStart(parentContext, exchange.getRequest())) {
        source.subscribe(actual);
        return;
      }
      Context currentContext = instrumenter.start(parentContext, exchange.getRequest());
      try (Scope ignored = currentContext.makeCurrent()) {
        this.source.subscribe(
            new TelemetryWrappedSubscriber(actual, currentContext, instrumenter, exchange));
      }
    }
  }

  private static class TelemetryWrappedSubscriber implements CoreSubscriber<Void> {

    private final CoreSubscriber<? super Void> actual;
    private final Instrumenter<ServerHttpRequest, ServerHttpResponse> instrumenter;
    private final Context currentOtelContext;
    private final ServerWebExchange exchange;

    TelemetryWrappedSubscriber(
        CoreSubscriber<? super Void> actual,
        Context currentOtelContext,
        Instrumenter<ServerHttpRequest, ServerHttpResponse> instrumenter,
        ServerWebExchange exchange) {
      this.actual = actual;
      this.instrumenter = instrumenter;
      this.currentOtelContext = currentOtelContext;
      this.exchange = exchange;
    }

    @Override
    public void onSubscribe(Subscription s) {
      actual.onSubscribe(s);
    }

    @Override
    public void onNext(Void unused) {}

    @Override
    public void onError(Throwable t) {
      onTerminal(currentOtelContext, exchange, t);
      actual.onError(t);
    }

    @Override
    public void onComplete() {
      onTerminal(currentOtelContext, exchange, null);
      actual.onComplete();
    }

    private void onTerminal(Context currentContext, ServerWebExchange exchange, Throwable t) {
      ServerHttpResponse response = exchange.getResponse();
      if (response.isCommitted()) {
        instrumenter.end(currentContext, exchange.getRequest(), exchange.getResponse(), t);
      } else {
        response.beforeCommit(
            () -> {
              instrumenter.end(currentContext, exchange.getRequest(), exchange.getResponse(), t);
              return Mono.empty();
            });
      }
    }
  }
}
