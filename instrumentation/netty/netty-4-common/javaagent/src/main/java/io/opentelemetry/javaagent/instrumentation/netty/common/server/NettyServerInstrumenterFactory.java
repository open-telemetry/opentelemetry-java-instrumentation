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
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.javaagent.instrumentation.netty.common.HttpRequestAndChannel;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyErrorHolder;

public final class NettyServerInstrumenterFactory {

  public static Instrumenter<HttpRequestAndChannel, HttpResponse> create(
      String instrumentationName) {

    final NettyHttpServerAttributesExtractor httpAttributesExtractor =
        new NettyHttpServerAttributesExtractor();

    return Instrumenter.<HttpRequestAndChannel, HttpResponse>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            HttpSpanNameExtractor.create(httpAttributesExtractor))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesExtractor))
        .addAttributesExtractor(httpAttributesExtractor)
        .addAttributesExtractor(new NettyNetServerAttributesExtractor())
        .addRequestMetrics(HttpServerMetrics.get())
        .addContextCustomizer(
            (context, request, attributes) -> {
              context = NettyErrorHolder.init(context);
              // netty is not exactly a "container", but it's the best match out of these
              return ServerSpanNaming.init(context, ServerSpanNaming.Source.CONTAINER);
            })
        .newServerInstrumenter(HttpRequestHeadersGetter.INSTANCE);
  }

  private NettyServerInstrumenterFactory() {}
}
