/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import javax.annotation.Nullable;
import org.reactivestreams.Subscription;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;
import reactor.util.annotation.NonNull;

final class TelemetryProducingWebFilter implements WebFilter, Ordered {

  private final Instrumenter<ServerWebExchange, ServerWebExchange> instrumenter;
  @Nullable private final HttpServerRouteGetter<ServerWebExchange> routeFallback;

  TelemetryProducingWebFilter(
      Instrumenter<ServerWebExchange, ServerWebExchange> instrumenter,
      @Nullable HttpServerRouteGetter<ServerWebExchange> routeFallback) {
    this.instrumenter = instrumenter;
    this.routeFallback = routeFallback;
  }

  @Override
  @NonNull
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    Context parentContext = Context.current();
    Mono<Void> source = chain.filter(exchange);
    return new TelemetryWrappedMono(source, instrumenter, parentContext, exchange, routeFallback);
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
    @Nullable private final HttpServerRouteGetter<ServerWebExchange> routeFallback;

    TelemetryWrappedMono(
        Mono<Void> source,
        Instrumenter<ServerWebExchange, ServerWebExchange> instrumenter,
        Context parentContext,
        ServerWebExchange exchange,
        @Nullable HttpServerRouteGetter<ServerWebExchange> routeFallback) {
      this.source = source;
      this.instrumenter = instrumenter;
      this.parentContext = parentContext;
      this.exchange = exchange;
      this.routeFallback = routeFallback;
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
            new TelemetryWrappedSubscriber(
                actual, currentContext, instrumenter, exchange, routeFallback));
      }
    }
  }

  private static class TelemetryWrappedSubscriber extends BaseSubscriber<Void> {
    private final CoreSubscriber<? super Void> actual;
    private final Instrumenter<ServerWebExchange, ServerWebExchange> instrumenter;
    private final Context currentOtelContext;
    private final ServerWebExchange exchange;
    @Nullable private final HttpServerRouteGetter<ServerWebExchange> routeFallback;

    TelemetryWrappedSubscriber(
        CoreSubscriber<? super Void> actual,
        Context currentOtelContext,
        Instrumenter<ServerWebExchange, ServerWebExchange> instrumenter,
        ServerWebExchange exchange,
        @Nullable HttpServerRouteGetter<ServerWebExchange> routeFallback) {
      this.actual = actual;
      this.instrumenter = instrumenter;
      this.currentOtelContext = currentOtelContext;
      this.exchange = exchange;
      this.routeFallback = routeFallback;
    }

    @Override
    public reactor.util.context.Context currentContext() {
      return actual.currentContext();
    }

    @Override
    protected void hookOnSubscribe(Subscription subscription) {
      actual.onSubscribe(this);
    }

    @Override
    protected void hookOnError(Throwable throwable) {
      onTerminal(currentOtelContext, throwable);
      actual.onError(throwable);
    }

    @Override
    protected void hookOnComplete() {
      onTerminal(currentOtelContext, null);
      actual.onComplete();
    }

    @Override
    protected void hookOnCancel() {
      end(currentOtelContext, true, null);
    }

    private void onTerminal(Context currentContext, @Nullable Throwable t) {
      ServerHttpResponse response = exchange.getResponse();
      if (response.isCommitted()) {
        end(currentContext, t);
      } else {
        response.beforeCommit(() -> {
          end(currentContext, t);
          return Mono.empty();
        });
      }
    }

    private void end(Context currentContext, @Nullable Throwable t) {
      end(currentContext, false, t);
    }

    private void end(Context currentContext, boolean cancel, @Nullable Throwable t) {
      // Update HTTP route now, because during instrumenter.start()
      // the HTTP route isn't available from the exchange attributes, but is afterwards
      HttpServerRoute.update(
          currentContext,
          HttpServerRouteSource.CONTROLLER,
          (context, exchange) ->
              exchange == null ? null : WebfluxServerHttpAttributesGetter.getRoute(exchange),
          exchange);
      if (routeFallback != null) {
        HttpServerRoute.update(
            currentContext, HttpServerRouteSource.SERVER, routeFallback, exchange);
      }
      instrumenter.end(
          currentContext,
          exchange,
          cancel && !exchange.getResponse().isCommitted() ? null : exchange,
          t);
    }
  }
}
