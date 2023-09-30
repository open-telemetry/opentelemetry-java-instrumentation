/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerExperimentalMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;

public final class GrizzlySingletons {

  private static final Instrumenter<HttpRequestPacket, HttpResponsePacket> INSTRUMENTER;

  static {
    GrizzlyHttpAttributesGetter httpAttributesGetter = new GrizzlyHttpAttributesGetter();

    InstrumenterBuilder<HttpRequestPacket, HttpResponsePacket> builder =
        Instrumenter.<HttpRequestPacket, HttpResponsePacket>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.grizzly-2.3",
                HttpSpanNameExtractor.builder(httpAttributesGetter)
                    .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
                    .build())
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(
                HttpServerAttributesExtractor.builder(httpAttributesGetter)
                    .setCapturedRequestHeaders(CommonConfig.get().getServerRequestHeaders())
                    .setCapturedResponseHeaders(CommonConfig.get().getServerResponseHeaders())
                    .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
                    .build())
            .addOperationMetrics(HttpServerMetrics.get());
    if (CommonConfig.get().shouldEmitExperimentalHttpServerMetrics()) {
      builder.addOperationMetrics(HttpServerExperimentalMetrics.get());
    }
    INSTRUMENTER =
        builder
            .addContextCustomizer(
                (context, request, attributes) ->
                    new AppServerBridge.Builder()
                        .captureServletAttributes()
                        .recordException()
                        .init(context))
            .addContextCustomizer(
                (context, httpRequestPacket, startAttributes) -> GrizzlyErrorHolder.init(context))
            .addContextCustomizer(
                HttpServerRoute.builder(httpAttributesGetter)
                    .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
                    .build())
            .buildServerInstrumenter(HttpRequestHeadersGetter.INSTANCE);
  }

  public static Instrumenter<HttpRequestPacket, HttpResponsePacket> instrumenter() {
    return INSTRUMENTER;
  }

  private GrizzlySingletons() {}
}
