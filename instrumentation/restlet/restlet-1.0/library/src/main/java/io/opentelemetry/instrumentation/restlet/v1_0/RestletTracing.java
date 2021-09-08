/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.restlet.data.Request;
import org.restlet.data.Response;

public final class RestletTracing {

  public static RestletTracing create(OpenTelemetry openTelemetry) {
    return newBuilder(openTelemetry).build();
  }

  public static RestletTracingBuilder newBuilder(OpenTelemetry openTelemetry) {
    return new RestletTracingBuilder(openTelemetry);
  }

  private final Instrumenter<Request, Response> serverInstrumenter;

  RestletTracing(Instrumenter<Request, Response> serverInstrumenter) {
    this.serverInstrumenter = serverInstrumenter;
  }

  public TracingFilter newFilter(String path) {
    return new TracingFilter(serverInstrumenter, path);
  }

  public Instrumenter<Request, Response> getServerInstrumenter() {
    return serverInstrumenter;
  }
}
