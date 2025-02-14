/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javahttpserver;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.io.IOException;

/** Decorates an {@link HttpServer} to trace inbound {@link HttpExchange}s. */
final class OpenTelemetryFilter extends Filter {

  private final Instrumenter<HttpExchange, HttpExchange> instrumenter;

  OpenTelemetryFilter(Instrumenter<HttpExchange, HttpExchange> instrumenter) {
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

    Throwable error = null;
    try (Scope ignored = context.makeCurrent()) {
      chain.doFilter(exchange);
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      instrumenter.end(context, exchange, exchange, error);
    }
  }

  @Override
  public String description() {
    return "OpenTelemetry tracing filter";
  }
}
