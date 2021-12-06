/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.CONTAINER;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.servlet.AppServerBridge;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.javaagent.bootstrap.undertow.UndertowActiveHandlers;
import io.undertow.server.HttpServerExchange;

public final class UndertowSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.undertow-1.4";

  private static final Instrumenter<HttpServerExchange, HttpServerExchange> INSTRUMENTER;

  static {
    HttpServerAttributesExtractor<HttpServerExchange, HttpServerExchange> httpAttributesExtractor =
        new UndertowHttpAttributesExtractor();
    SpanNameExtractor<HttpServerExchange> spanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesExtractor);
    SpanStatusExtractor<HttpServerExchange, HttpServerExchange> spanStatusExtractor =
        HttpSpanStatusExtractor.create(httpAttributesExtractor);
    NetServerAttributesExtractor<HttpServerExchange, HttpServerExchange> netAttributesExtractor =
        new UndertowNetAttributesExtractor();

    INSTRUMENTER =
        Instrumenter.<HttpServerExchange, HttpServerExchange>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .setSpanStatusExtractor(spanStatusExtractor)
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(netAttributesExtractor)
            .addContextCustomizer(
                (context, request, attributes) -> {
                  context = ServerSpanNaming.init(context, CONTAINER);
                  // span is ended when counter reaches 0, we start from 2 which accounts for the
                  // handler that started the span and exchange completion listener
                  context = UndertowActiveHandlers.init(context, 2);

                  return new AppServerBridge.Builder()
                      .captureServletAttributes()
                      .recordException()
                      .init(context);
                })
            .addRequestMetrics(HttpServerMetrics.get())
            .newServerInstrumenter(UndertowExchangeGetter.INSTANCE);
  }

  private static final UndertowHelper HELPER = new UndertowHelper(INSTRUMENTER);

  public static UndertowHelper helper() {
    return HELPER;
  }

  private UndertowSingletons() {}
}
