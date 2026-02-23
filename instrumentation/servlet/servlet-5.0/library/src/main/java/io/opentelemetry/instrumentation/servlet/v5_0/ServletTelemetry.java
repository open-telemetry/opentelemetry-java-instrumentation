/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v5_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.servlet.internal.ServletRequestContext;
import io.opentelemetry.instrumentation.servlet.internal.ServletResponseContext;
import io.opentelemetry.instrumentation.servlet.v5_0.internal.Servlet5TelemetryFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Entrypoint for instrumenting Servlets. */
public final class ServletTelemetry {

  /** Returns a new instance configured with the given {@link OpenTelemetry} instance. */
  public static ServletTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a builder configured with the given {@link OpenTelemetry} instance. */
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

  /** Returns a {@link Filter} that instruments HTTP requests. */
  public Filter createFilter() {
    return new Servlet5TelemetryFilter(instrumenter, addTraceIdRequestAttribute);
  }
}
