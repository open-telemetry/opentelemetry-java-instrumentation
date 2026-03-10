/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.servlet.internal.ServletRequestContext;
import io.opentelemetry.instrumentation.servlet.internal.ServletResponseContext;
import io.opentelemetry.instrumentation.servlet.v3_0.internal.Servlet3Accessor;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import io.opentelemetry.javaagent.instrumentation.servlet.AgentServletInstrumenterBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public final class LibertySingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.liberty-20.0";

  private static final Instrumenter<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      INSTRUMENTER =
          AgentServletInstrumenterBuilder.<HttpServletRequest, HttpServletResponse>create()
              .addContextCustomizer(
                  (context, request, attributes) ->
                      new AppServerBridge.Builder().recordException().init(context))
              .propagateOperationListenersToOnEnd()
              .build(INSTRUMENTATION_NAME, Servlet3Accessor.INSTANCE);

  private static final LibertyHelper<HttpServletRequest, HttpServletResponse> HELPER =
      new LibertyHelper<>(INSTRUMENTER, Servlet3Accessor.INSTANCE);

  public static LibertyHelper<HttpServletRequest, HttpServletResponse> helper() {
    return HELPER;
  }

  private LibertySingletons() {}
}
