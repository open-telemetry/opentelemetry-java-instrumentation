/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.server.ServerSpanNaming;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;

public final class GrizzlySingletons {

  private static final Instrumenter<HttpRequestPacket, HttpResponsePacket> INSTRUMENTER;

  static {
    GrizzlyHttpAttributesExtractor httpAttributesExtractor = new GrizzlyHttpAttributesExtractor();
    GrizzlyNetAttributesExtractor netAttributesExtractor = new GrizzlyNetAttributesExtractor();

    INSTRUMENTER =
        Instrumenter.<HttpRequestPacket, HttpResponsePacket>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.grizzly-2.0",
                HttpSpanNameExtractor.create(httpAttributesExtractor))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesExtractor))
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(netAttributesExtractor)
            .addRequestMetrics(HttpServerMetrics.get())
            .addContextCustomizer(
                (context, httpRequestPacket, startAttributes) -> GrizzlyErrorHolder.init(context))
            .addContextCustomizer(ServerSpanNaming.get())
            .newServerInstrumenter(HttpRequestHeadersGetter.INSTANCE);
  }

  public static Instrumenter<HttpRequestPacket, HttpResponsePacket> instrumenter() {
    return INSTRUMENTER;
  }

  private GrizzlySingletons() {}
}
