/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.CONTAINER;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletInstrumenterBuilder;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletRequestContext;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletResponseContext;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3Accessor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public final class LibertySingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.liberty";

  private static final Instrumenter<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      INSTRUMENTER =
          ServletInstrumenterBuilder.<HttpServletRequest, HttpServletResponse>create()
              .addContextCustomizer(
                  (context, request, attributes) -> {
                    context = ServerSpanNaming.init(context, CONTAINER);
                    return new AppServerBridge.Builder().recordException().init(context);
                  })
              .build(INSTRUMENTATION_NAME, Servlet3Accessor.INSTANCE);

  private static final LibertyHelper<HttpServletRequest, HttpServletResponse> HELPER =
      new LibertyHelper<>(INSTRUMENTER, Servlet3Accessor.INSTANCE);

  public static LibertyHelper<HttpServletRequest, HttpServletResponse> helper() {
    return HELPER;
  }

  private LibertySingletons() {}
}
