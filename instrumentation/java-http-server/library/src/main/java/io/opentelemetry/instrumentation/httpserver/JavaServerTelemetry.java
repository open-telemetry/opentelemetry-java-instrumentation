/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpserver;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/** Entrypoint for instrumenting the jdk.httpserver services. */
public final class JavaServerTelemetry {

  /** Returns a new {@link JavaServerTelemetry} configured with the given {@link OpenTelemetry}. */
  public static JavaServerTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  public static JavaServerTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new JavaServerTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<HttpExchange, HttpExchange> instrumenter;

  JavaServerTelemetry(Instrumenter<HttpExchange, HttpExchange> instrumenter) {
    this.instrumenter = instrumenter;
  }

  /** Returns a new {@link Filter} for telemetry usage */
  public Filter otelFilter() {
    return new OpenTelemetryFilter(instrumenter);
  }
}
