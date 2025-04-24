/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.helidon;

import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/** Entrypoint for instrumenting Java HTTP Server services. */
public final class HelidonTelemetry implements HttpFeature {

  /** Returns a new {@link HelidonTelemetry} configured with the given {@link OpenTelemetry}. */
  public static HelidonTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  public static HelidonTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new HelidonTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<ServerRequest, ServerResponse> instrumenter;

  HelidonTelemetry(Instrumenter<ServerRequest, ServerResponse> instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  public void setup(HttpRouting.Builder routing) {
    routing.addFilter(new OpenTelemetryFilter(instrumenter));
  }
}
