/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4.common.internal.server;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import io.opentelemetry.instrumentation.netty.common.internal.NettyErrorHolder;
import io.opentelemetry.instrumentation.netty.v4.common.internal.HttpRequestAndChannel;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class NettyServerInstrumenterFactory {

  public static Instrumenter<HttpRequestAndChannel, HttpResponse> create(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      List<String> capturedRequestHeaders,
      List<String> capturedResponseHeaders) {

    NettyHttpServerAttributesGetter httpAttributesGetter = new NettyHttpServerAttributesGetter();

    return Instrumenter.<HttpRequestAndChannel, HttpResponse>builder(
            openTelemetry, instrumentationName, HttpSpanNameExtractor.create(httpAttributesGetter))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
        .addAttributesExtractor(
            HttpServerAttributesExtractor.builder(httpAttributesGetter)
                .setCapturedRequestHeaders(capturedRequestHeaders)
                .setCapturedResponseHeaders(capturedResponseHeaders)
                .build())
        .addAttributesExtractor(
            NetServerAttributesExtractor.create(new NettyNetServerAttributesGetter()))
        .addOperationMetrics(HttpServerMetrics.get())
        .addContextCustomizer((context, request, attributes) -> NettyErrorHolder.init(context))
        .addContextCustomizer(HttpRouteHolder.get())
        .buildServerInstrumenter(HttpRequestHeadersGetter.INSTANCE);
  }

  private NettyServerInstrumenterFactory() {}
}
