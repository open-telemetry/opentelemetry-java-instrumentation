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
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectRequest;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyErrorHolder;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.HttpRequestAndChannel;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpResponse;

public final class NettyClientSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.netty-3.8";

  private static final Instrumenter<HttpRequestAndChannel, HttpResponse> INSTRUMENTER;
  private static final Instrumenter<NettyConnectRequest, Channel> CONNECT_INSTRUMENTER;

  static {
    NettyHttpClientAttributesExtractor httpClientAttributesExtractor =
        new NettyHttpClientAttributesExtractor();
    NettyNetClientAttributesExtractor netClientAttributesExtractor =
        new NettyNetClientAttributesExtractor();

    INSTRUMENTER =
        Instrumenter.<HttpRequestAndChannel, HttpResponse>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpClientAttributesExtractor))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpClientAttributesExtractor))
            .addAttributesExtractor(httpClientAttributesExtractor)
            .addAttributesExtractor(netClientAttributesExtractor)
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(netClientAttributesExtractor))
            .addRequestMetrics(HttpClientMetrics.get())
            .addContextCustomizer(
                (context, requestAndChannel, startAttributes) -> NettyErrorHolder.init(context))
            .newClientInstrumenter(new HttpRequestHeadersSetter());

    NettyConnectNetAttributesExtractor nettyConnectAttributesExtractor =
        new NettyConnectNetAttributesExtractor();
    CONNECT_INSTRUMENTER =
        Instrumenter.<NettyConnectRequest, Channel>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, rq -> "CONNECT")
            .addAttributesExtractor(nettyConnectAttributesExtractor)
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(nettyConnectAttributesExtractor))
            .setTimeExtractors(
                request -> request.timer().startTime(),
                (request, channel, error) -> request.timer().now())
            .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  public static Instrumenter<NettyConnectRequest, Channel> connectInstrumenter() {
    return CONNECT_INSTRUMENTER;
  }

  private NettyClientSingletons() {}
}
