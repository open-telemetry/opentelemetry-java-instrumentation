/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerExperimentalMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.netty.common.internal.NettyErrorHolder;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.HttpRequestAndChannel;
import org.jboss.netty.handler.codec.http.HttpResponse;

final class NettyServerSingletons {

  private static final Instrumenter<HttpRequestAndChannel, HttpResponse> INSTRUMENTER;

  static {
    NettyHttpServerAttributesGetter httpServerAttributesGetter =
        new NettyHttpServerAttributesGetter();

    InstrumenterBuilder<HttpRequestAndChannel, HttpResponse> builder =
        Instrumenter.<HttpRequestAndChannel, HttpResponse>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.netty-3.8",
                HttpSpanNameExtractor.builder(httpServerAttributesGetter)
                    .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
                    .build())
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpServerAttributesGetter))
            .addAttributesExtractor(
                HttpServerAttributesExtractor.builder(httpServerAttributesGetter)
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
                (context, requestAndChannel, startAttributes) -> NettyErrorHolder.init(context))
            .addContextCustomizer(
                HttpServerRoute.builder(httpServerAttributesGetter)
                    .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
                    .build())
            .buildServerInstrumenter(NettyHeadersGetter.INSTANCE);
  }

  public static Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private NettyServerSingletons() {}
}
