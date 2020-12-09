/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty;

import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3HttpServerInstrumenter;

public class JettyHttpServerInstrumenter extends Servlet3HttpServerInstrumenter {
  private static final JettyHttpServerInstrumenter TRACER = new JettyHttpServerInstrumenter();

  public static JettyHttpServerInstrumenter tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.jetty";
  }
}
