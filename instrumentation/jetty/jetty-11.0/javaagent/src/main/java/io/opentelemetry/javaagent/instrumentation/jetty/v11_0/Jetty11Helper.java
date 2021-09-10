/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v11_0;

import static io.opentelemetry.javaagent.instrumentation.jetty.v11_0.Jetty11Singletons.instrumenter;

import io.opentelemetry.javaagent.instrumentation.jetty.common.JettyHelper;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.Servlet5Accessor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class Jetty11Helper extends JettyHelper<HttpServletRequest, HttpServletResponse> {
  private static final Jetty11Helper HELPER = new Jetty11Helper();

  public static Jetty11Helper helper() {
    return HELPER;
  }

  private Jetty11Helper() {
    super(instrumenter(), Servlet5Accessor.INSTANCE);
  }
}
