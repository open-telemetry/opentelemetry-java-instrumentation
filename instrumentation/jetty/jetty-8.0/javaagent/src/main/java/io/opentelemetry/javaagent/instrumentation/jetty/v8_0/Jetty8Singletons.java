/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v8_0;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.CONTAINER;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.servlet.AppServerBridge;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.servlet.v3_0.Servlet3Accessor;
import io.opentelemetry.javaagent.instrumentation.jetty.common.JettyHelper;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletInstrumenterBuilder;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletRequestContext;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletResponseContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public final class Jetty8Singletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jetty-8.0";

  private static final Instrumenter<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      INSTRUMENTER =
          ServletInstrumenterBuilder.<HttpServletRequest, HttpServletResponse>create()
              .addContextCustomizer(
                  (context, request, attributes) -> {
                    context = ServerSpanNaming.init(context, CONTAINER);
                    return AppServerBridge.init(context, /* shouldRecordException= */ false);
                  })
              .build(INSTRUMENTATION_NAME, Servlet3Accessor.INSTANCE);

  private static final JettyHelper<HttpServletRequest, HttpServletResponse> HELPER =
      new JettyHelper<>(INSTRUMENTER, Servlet3Accessor.INSTANCE);

  public static JettyHelper<HttpServletRequest, HttpServletResponse> helper() {
    return HELPER;
  }

  private Jetty8Singletons() {}
}
