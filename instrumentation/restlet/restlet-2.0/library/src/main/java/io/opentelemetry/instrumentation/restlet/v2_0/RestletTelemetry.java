/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.routing.Filter;

/** Entrypoint for instrumenting Restlet servers. */
public final class RestletTelemetry {

  /** Returns a new {@link RestletTelemetry} configured with the given {@link OpenTelemetry}. */
  public static RestletTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link RestletTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static RestletTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new RestletTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<Request, Response> serverInstrumenter;

  RestletTelemetry(Instrumenter<Request, Response> serverInstrumenter) {
    this.serverInstrumenter = serverInstrumenter;
  }

  /**
   * Returns a new {@link Filter} which can be used to wrap {@link org.restlet.Restlet}
   * implementations.
   */
  public Filter newFilter(String path) {
    return new TracingFilter(serverInstrumenter, path);
  }
}
