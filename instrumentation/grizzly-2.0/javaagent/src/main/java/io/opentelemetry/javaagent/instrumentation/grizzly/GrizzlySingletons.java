/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;

public final class GrizzlySingletons {

  private static final Instrumenter<HttpRequestPacket, HttpResponsePacket> INSTRUMENTER;

  static {
    GrizzlyHttpAttributesGetter httpAttributesGetter = new GrizzlyHttpAttributesGetter();
    GrizzlyNetAttributesGetter netAttributesGetter = new GrizzlyNetAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<HttpRequestPacket, HttpResponsePacket>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.grizzly-2.0",
                HttpSpanNameExtractor.create(httpAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(HttpServerAttributesExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(NetServerAttributesExtractor.create(netAttributesGetter))
            .addOperationMetrics(HttpServerMetrics.get())
            .addContextCustomizer(
                (context, request, attributes) ->
                    new AppServerBridge.Builder().recordException().init(context))
            .addContextCustomizer(
                (context, httpRequestPacket, startAttributes) -> GrizzlyErrorHolder.init(context))
            .addContextCustomizer(HttpRouteHolder.get())
            .newServerInstrumenter(HttpRequestHeadersGetter.INSTANCE);
  }

  public static Instrumenter<HttpRequestPacket, HttpResponsePacket> instrumenter() {
    return INSTRUMENTER;
  }

  private GrizzlySingletons() {}
}
