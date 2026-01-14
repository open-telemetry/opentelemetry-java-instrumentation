/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_1;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.restlet.Filter;
import org.restlet.data.Request;
import org.restlet.data.Response;

/** Entrypoint for instrumenting Restlet servers. */
public final class RestletTelemetry {

  /** Returns a new instance configured with the given {@link OpenTelemetry} instance. */
  public static RestletTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a builder configured with the given {@link OpenTelemetry} instance. */
  public static RestletTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new RestletTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<Request, Response> serverInstrumenter;

  RestletTelemetry(Instrumenter<Request, Response> serverInstrumenter) {
    this.serverInstrumenter = serverInstrumenter;
  }

  /** Returns a {@link Filter} that instruments HTTP requests. */
  public Filter createFilter(String path) {
    return new TracingFilter(serverInstrumenter, path);
  }

  /**
   * Returns a {@link Filter} that instruments HTTP requests.
   *
   * @deprecated Use {@link #createFilter(String)} instead.
   */
  @Deprecated
  public Filter newFilter(String path) {
    return createFilter(path);
  }
}
