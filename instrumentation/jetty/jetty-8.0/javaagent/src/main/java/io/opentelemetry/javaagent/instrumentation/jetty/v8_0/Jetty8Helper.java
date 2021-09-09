/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v8_0;

import static io.opentelemetry.javaagent.instrumentation.jetty.v8_0.Jetty8Singletons.instrumenter;

import io.opentelemetry.instrumentation.servlet.v3_0.Servlet3Accessor;
import io.opentelemetry.javaagent.instrumentation.jetty.common.JettyHelper;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Jetty8Helper extends JettyHelper<HttpServletRequest, HttpServletResponse> {
  private static final Jetty8Helper HELPER = new Jetty8Helper();

  public static Jetty8Helper helper() {
    return HELPER;
  }

  private Jetty8Helper() {
    super(instrumenter(), Servlet3Accessor.INSTANCE);
  }
}
