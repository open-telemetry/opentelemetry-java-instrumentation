/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.server;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.server.ServerSpanNaming;
import io.opentelemetry.javaagent.instrumentation.netty.common.HttpRequestAndChannel;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyErrorHolder;

public final class NettyServerInstrumenterFactory {

  public static Instrumenter<HttpRequestAndChannel, HttpResponse> create(
      String instrumentationName) {

    NettyHttpServerAttributesExtractor httpAttributesExtractor =
        new NettyHttpServerAttributesExtractor();

    return Instrumenter.<HttpRequestAndChannel, HttpResponse>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            HttpSpanNameExtractor.create(httpAttributesExtractor))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesExtractor))
        .addAttributesExtractor(httpAttributesExtractor)
        .addAttributesExtractor(new NettyNetServerAttributesExtractor())
        .addRequestMetrics(HttpServerMetrics.get())
        .addContextCustomizer((context, request, attributes) -> NettyErrorHolder.init(context))
        .addContextCustomizer(ServerSpanNaming.get())
        .newServerInstrumenter(HttpRequestHeadersGetter.INSTANCE);
  }

  private NettyServerInstrumenterFactory() {}
}
