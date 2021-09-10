/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v11_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.servlet.ServletInstrumenterBuilder;
import io.opentelemetry.instrumentation.servlet.ServletRequestContext;
import io.opentelemetry.instrumentation.servlet.ServletResponseContext;
import io.opentelemetry.instrumentation.servlet.jakarta.v5_0.Servlet5Accessor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class Jetty11Singletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jetty-11.0";

  private static final Instrumenter<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      INSTRUMENTER =
          ServletInstrumenterBuilder.newInstrumenter(
              INSTRUMENTATION_NAME, Servlet5Accessor.INSTANCE);

  public static Instrumenter<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      instrumenter() {
    return INSTRUMENTER;
  }

  private Jetty11Singletons() {}
}
