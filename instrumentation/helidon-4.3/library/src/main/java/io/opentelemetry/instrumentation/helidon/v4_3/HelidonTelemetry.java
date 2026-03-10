/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.helidon.v4_3;

import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/** Entrypoint for instrumenting Helidon services. */
public final class HelidonTelemetry {

  /** Returns a new instance configured with the given {@link OpenTelemetry} instance. */
  public static HelidonTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a builder configured with the given {@link OpenTelemetry} instance. */
  public static HelidonTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new HelidonTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<ServerRequest, ServerResponse> instrumenter;

  HelidonTelemetry(Instrumenter<ServerRequest, ServerResponse> instrumenter) {
    this.instrumenter = instrumenter;
  }

  /** Returns a {@link Filter} that instruments HTTP server requests. */
  public Filter createFilter() {
    return new OpenTelemetryFilter(instrumenter);
  }
}
