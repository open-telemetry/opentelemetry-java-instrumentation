/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpserver;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/** Entrypoint for instrumenting Armeria services. */
public final class JdkServerTelemetry {

  /** Returns a new {@link JdkServerTelemetry} configured with the given {@link OpenTelemetry}. */
  public static JdkServerTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  public static JdkServerTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new JdkServerTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<HttpExchange, HttpExchange> instrumenter;

  JdkServerTelemetry(Instrumenter<HttpExchange, HttpExchange> instrumenter) {
    this.instrumenter = instrumenter;
  }

  /** Returns a new {@link Filter} for telemetry usage */
  public Filter otelFilter() {
    return new OpenTelemetryService(instrumenter);
  }
}
