/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v11_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import io.opentelemetry.javaagent.instrumentation.jetty.common.JettyHelper;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletInstrumenterBuilder;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletRequestContext;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletResponseContext;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.Servlet5Accessor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class Jetty11Singletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jetty-11.0";

  private static final Instrumenter<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      INSTRUMENTER =
          ServletInstrumenterBuilder.<HttpServletRequest, HttpServletResponse>create()
              .addContextCustomizer(
                  (context, request, attributes) -> new AppServerBridge.Builder().init(context))
              .propagateOperationListenersToOnEnd()
              .build(INSTRUMENTATION_NAME, Servlet5Accessor.INSTANCE);

  private static final JettyHelper<HttpServletRequest, HttpServletResponse> HELPER =
      new JettyHelper<>(INSTRUMENTER, Servlet5Accessor.INSTANCE);

  public static JettyHelper<HttpServletRequest, HttpServletResponse> helper() {
    return HELPER;
  }

  private Jetty11Singletons() {}
}
