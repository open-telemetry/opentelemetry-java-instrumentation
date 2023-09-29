/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientExperimentalMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientPeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.netty.common.internal.NettyConnectionRequest;
import io.opentelemetry.instrumentation.netty.common.internal.NettyErrorHolder;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.HttpRequestAndChannel;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpResponse;

public final class NettyClientSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.netty-3.8";

  private static final Instrumenter<HttpRequestAndChannel, HttpResponse> INSTRUMENTER;
  private static final Instrumenter<NettyConnectionRequest, Channel> CONNECTION_INSTRUMENTER;

  static {
    NettyHttpClientAttributesGetter httpAttributesGetter = new NettyHttpClientAttributesGetter();

    InstrumenterBuilder<HttpRequestAndChannel, HttpResponse> builder =
        Instrumenter.<HttpRequestAndChannel, HttpResponse>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.builder(httpAttributesGetter)
                    .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
                    .build())
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(
                HttpClientAttributesExtractor.builder(httpAttributesGetter)
                    .setCapturedRequestHeaders(CommonConfig.get().getClientRequestHeaders())
                    .setCapturedResponseHeaders(CommonConfig.get().getClientResponseHeaders())
                    .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
                    .build())
            .addAttributesExtractor(
                HttpClientPeerServiceAttributesExtractor.create(
                    httpAttributesGetter, CommonConfig.get().getPeerServiceResolver()))
            .addOperationMetrics(HttpClientMetrics.get())
            .addContextCustomizer(
                (context, requestAndChannel, startAttributes) -> NettyErrorHolder.init(context));
    if (CommonConfig.get().shouldEmitExperimentalHttpClientMetrics()) {
      builder.addOperationMetrics(HttpClientExperimentalMetrics.get());
    }
    INSTRUMENTER = builder.buildClientInstrumenter(HttpRequestHeadersSetter.INSTANCE);

    CONNECTION_INSTRUMENTER =
        Instrumenter.<NettyConnectionRequest, Channel>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, NettyConnectionRequest::spanName)
            .addAttributesExtractor(
                HttpClientAttributesExtractor.create(NettyConnectHttpAttributesGetter.INSTANCE))
            .addAttributesExtractor(
                HttpClientPeerServiceAttributesExtractor.create(
                    NettyConnectHttpAttributesGetter.INSTANCE,
                    CommonConfig.get().getPeerServiceResolver()))
            .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  public static Instrumenter<NettyConnectionRequest, Channel> connectionInstrumenter() {
    return CONNECTION_INSTRUMENTER;
  }

  private NettyClientSingletons() {}
}
