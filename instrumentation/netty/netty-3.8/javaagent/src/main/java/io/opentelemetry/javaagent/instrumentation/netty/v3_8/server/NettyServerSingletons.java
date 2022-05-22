/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyErrorHolder;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.HttpRequestAndChannel;
import org.jboss.netty.handler.codec.http.HttpResponse;

final class NettyServerSingletons {

  private static final Instrumenter<HttpRequestAndChannel, HttpResponse> INSTRUMENTER;

  static {
    NettyHttpServerAttributesGetter httpServerAttributesGetter =
        new NettyHttpServerAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<HttpRequestAndChannel, HttpResponse>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.netty-3.8",
                HttpSpanNameExtractor.create(httpServerAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpServerAttributesGetter))
            .addAttributesExtractor(
                HttpServerAttributesExtractor.create(httpServerAttributesGetter))
            .addAttributesExtractor(
                NetServerAttributesExtractor.create(new NettyNetServerAttributesGetter()))
            .addOperationMetrics(HttpServerMetrics.get())
            .addContextCustomizer(
                (context, requestAndChannel, startAttributes) -> NettyErrorHolder.init(context))
            .addContextCustomizer(HttpRouteHolder.get())
            .newServerInstrumenter(NettyHeadersGetter.INSTANCE);
  }

  public static Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private NettyServerSingletons() {}
}
