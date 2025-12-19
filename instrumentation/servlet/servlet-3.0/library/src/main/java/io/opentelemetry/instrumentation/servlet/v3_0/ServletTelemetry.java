/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.servlet.internal.ServletRequestContext;
import io.opentelemetry.instrumentation.servlet.internal.ServletResponseContext;
import io.opentelemetry.instrumentation.servlet.v3_0.internal.Servlet3TelemetryFilter;
import javax.servlet.Filter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Entrypoint for instrumenting Servlets. */
public final class ServletTelemetry {

  /** Returns a new {@link ServletTelemetry} configured with the given {@link OpenTelemetry}. */
  public static ServletTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link ServletTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static ServletTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new ServletTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      instrumenter;
  private final boolean addTraceIdRequestAttribute;

  ServletTelemetry(
      Instrumenter<
              ServletRequestContext<HttpServletRequest>,
              ServletResponseContext<HttpServletResponse>>
          instrumenter,
      boolean addTraceIdRequestAttribute) {
    this.instrumenter = instrumenter;
    this.addTraceIdRequestAttribute = addTraceIdRequestAttribute;
  }

  /** Returns a new {@link Filter} for producing telemetry. */
  public Filter newFilter() {
    return new Servlet3TelemetryFilter(instrumenter, addTraceIdRequestAttribute);
  }
}
