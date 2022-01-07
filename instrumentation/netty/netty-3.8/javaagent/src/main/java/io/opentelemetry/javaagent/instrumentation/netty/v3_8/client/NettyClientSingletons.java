/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
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
    NettyHttpClientAttributesExtractor httpClientAttributesExtractor =
        new NettyHttpClientAttributesExtractor();
    NettyNetClientAttributesAdapter netClientAttributesAdapter =
        new NettyNetClientAttributesAdapter();
    NetClientAttributesExtractor<HttpRequestAndChannel, HttpResponse> netClientAttributesExtractor =
        NetClientAttributesExtractor.create(netClientAttributesAdapter);

    INSTRUMENTER =
        Instrumenter.<HttpRequestAndChannel, HttpResponse>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpClientAttributesExtractor))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpClientAttributesExtractor))
            .addAttributesExtractor(httpClientAttributesExtractor)
            .addAttributesExtractor(netClientAttributesExtractor)
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(netClientAttributesAdapter))
            .addRequestMetrics(HttpClientMetrics.get())
            .addContextCustomizer(
                (context, requestAndChannel, startAttributes) -> NettyErrorHolder.init(context))
            .newClientInstrumenter(HttpRequestHeadersSetter.INSTANCE);

    NettyConnectNetAttributesAdapter nettyConnectAttributesAdapter =
        new NettyConnectNetAttributesAdapter();
    NetClientAttributesExtractor<NettyConnectionRequest, Channel> nettyConnectAttributesExtractor =
        NetClientAttributesExtractor.create(nettyConnectAttributesAdapter);
    CONNECTION_INSTRUMENTER =
        Instrumenter.<NettyConnectionRequest, Channel>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, NettyConnectionRequest::spanName)
            .addAttributesExtractor(nettyConnectAttributesExtractor)
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(nettyConnectAttributesAdapter))
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
