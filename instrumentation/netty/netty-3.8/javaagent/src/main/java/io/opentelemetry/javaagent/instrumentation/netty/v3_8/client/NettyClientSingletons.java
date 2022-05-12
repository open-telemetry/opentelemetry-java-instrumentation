/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectionRequest;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyErrorHolder;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.HttpRequestAndChannel;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpResponse;

public final class NettyClientSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.netty-3.8";

  private static final Instrumenter<HttpRequestAndChannel, HttpResponse> INSTRUMENTER;
  private static final Instrumenter<NettyConnectionRequest, Channel> CONNECTION_INSTRUMENTER;

  static {
    NettyHttpClientAttributesGetter httpClientAttributesGetter =
        new NettyHttpClientAttributesGetter();
    NettyNetClientAttributesGetter netClientAttributesGetter = new NettyNetClientAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<HttpRequestAndChannel, HttpResponse>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpClientAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpClientAttributesGetter))
            .addAttributesExtractor(
                HttpClientAttributesExtractor.create(httpClientAttributesGetter))
            .addAttributesExtractor(NetClientAttributesExtractor.create(netClientAttributesGetter))
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(netClientAttributesGetter))
            .addOperationMetrics(HttpClientMetrics.get())
            .addContextCustomizer(
                (context, requestAndChannel, startAttributes) -> NettyErrorHolder.init(context))
            .newClientInstrumenter(HttpRequestHeadersSetter.INSTANCE);

    NettyConnectNetAttributesGetter nettyConnectAttributesGetter =
        new NettyConnectNetAttributesGetter();
    NetClientAttributesExtractor<NettyConnectionRequest, Channel> nettyConnectAttributesExtractor =
        NetClientAttributesExtractor.create(nettyConnectAttributesGetter);
    CONNECTION_INSTRUMENTER =
        Instrumenter.<NettyConnectionRequest, Channel>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, NettyConnectionRequest::spanName)
            .addAttributesExtractor(nettyConnectAttributesExtractor)
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(nettyConnectAttributesGetter))
            .setTimeExtractor(new NettyConnectionTimeExtractor())
            .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  public static Instrumenter<NettyConnectionRequest, Channel> connectionInstrumenter() {
    return CONNECTION_INSTRUMENTER;
  }

  private NettyClientSingletons() {}
}
