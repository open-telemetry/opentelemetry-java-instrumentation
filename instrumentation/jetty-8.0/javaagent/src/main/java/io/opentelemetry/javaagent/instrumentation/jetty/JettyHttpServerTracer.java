/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty;

import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3HttpServerTracer;

public class JettyHttpServerTracer extends Servlet3HttpServerTracer {
  private static final JettyHttpServerTracer TRACER = new JettyHttpServerTracer();

  public static JettyHttpServerTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.jetty";
  }
}
