/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import io.opentelemetry.javaagent.bootstrap.undertow.UndertowActiveHandlers;
import io.undertow.server.HttpServerExchange;

public final class UndertowSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.undertow-1.4";

  private static final Instrumenter<HttpServerExchange, HttpServerExchange> INSTRUMENTER;

  static {
    INSTRUMENTER =
        JavaagentHttpServerInstrumenters.create(
            INSTRUMENTATION_NAME,
            new UndertowHttpAttributesGetter(),
            UndertowExchangeGetter.INSTANCE,
            builder ->
                builder.addContextCustomizer(
                    (context, request, attributes) -> {
                      // span is ended when counter reaches 0, we start from 2 which accounts for
                      // the handler that started the span and exchange completion listener
                      context = UndertowActiveHandlers.init(context, 2);

                      return new AppServerBridge.Builder()
                          .captureServletAttributes()
                          .recordException()
                          .init(context);
                    }));
  }

  private static final UndertowHelper HELPER = new UndertowHelper(INSTRUMENTER);

  public static UndertowHelper helper() {
    return HELPER;
  }

  private UndertowSingletons() {}
}
