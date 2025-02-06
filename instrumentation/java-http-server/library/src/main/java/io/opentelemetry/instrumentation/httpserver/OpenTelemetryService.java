package io.opentelemetry.instrumentation.httpserver;

import java.io.IOException;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/** Decorates an {@link HttpServer} to trace inbound {@link HttpExchange}s. */
final class OpenTelemetryService extends Filter {

  private final Instrumenter<HttpExchange, HttpExchange> instrumenter;

  OpenTelemetryService(Instrumenter<HttpExchange, HttpExchange> instrumenter) {

    this.instrumenter = instrumenter;
  }

  @Override
  public void doFilter(HttpExchange exchange, Chain chain) throws IOException {

    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, exchange)) {
      chain.doFilter(exchange);
      return;
    }

    Context context = instrumenter.start(parentContext, exchange);

    try (Scope ignored = context.makeCurrent()) {
      chain.doFilter(exchange);
    } finally {
      instrumenter.end(context, exchange, exchange, null);
    }
  }

  @Override
  public String description() {
    return "OpenTelemetry";
  }
}
