/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javahttpserver;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/** Entrypoint for instrumenting Java HTTP Server services. */
public final class JavaHttpServerTelemetry {

  /**
   * Returns a new {@link JavaHttpServerTelemetry} configured with the given {@link OpenTelemetry}.
   */
  public static JavaHttpServerTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  public static JavaHttpServerTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new JavaHttpServerTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<HttpExchange, HttpExchange> instrumenter;

  JavaHttpServerTelemetry(Instrumenter<HttpExchange, HttpExchange> instrumenter) {
    this.instrumenter = instrumenter;
  }

  /** Returns a new {@link Filter} for telemetry usage */
  public Filter newFilter() {
    return new OpenTelemetryFilter(instrumenter);
  }

  /** Configures the {@link HttpContext} with OpenTelemetry. */
  public void configure(HttpContext httpContext) {
    httpContext.getFilters().add(0, newFilter());
  }
}
