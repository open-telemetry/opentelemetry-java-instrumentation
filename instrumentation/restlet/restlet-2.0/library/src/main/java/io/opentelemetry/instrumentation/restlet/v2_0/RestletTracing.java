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

/** Entrypoint for tracing Restlet servers. */
public final class RestletTracing {

  /** Returns a new {@link RestletTracing} configured with the given {@link OpenTelemetry}. */
  public static RestletTracing create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link RestletTracingBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static RestletTracingBuilder builder(OpenTelemetry openTelemetry) {
    return new RestletTracingBuilder(openTelemetry);
  }

  private final Instrumenter<Request, Response> serverInstrumenter;

  RestletTracing(Instrumenter<Request, Response> serverInstrumenter) {
    this.serverInstrumenter = serverInstrumenter;
  }

  /**
   * Returns a new {@link Filter} which can be used to wrap {@link org.restlet.Restlet}
   * implementations.
   */
  public Filter newFilter(String path) {
    return new TracingFilter(serverInstrumenter, path);
  }

  /** Returns a server {@link Instrumenter}. */
  public Instrumenter<Request, Response> getServerInstrumenter() {
    return serverInstrumenter;
  }
}
