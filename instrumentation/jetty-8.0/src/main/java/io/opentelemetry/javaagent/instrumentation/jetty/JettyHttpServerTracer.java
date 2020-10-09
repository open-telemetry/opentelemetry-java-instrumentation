/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.jetty;

import io.opentelemetry.instrumentation.auto.servlet.v3_0.Servlet3HttpServerTracer;

public class JettyHttpServerTracer extends Servlet3HttpServerTracer {
  public static final JettyHttpServerTracer TRACER = new JettyHttpServerTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.jetty-8.0";
  }
}
