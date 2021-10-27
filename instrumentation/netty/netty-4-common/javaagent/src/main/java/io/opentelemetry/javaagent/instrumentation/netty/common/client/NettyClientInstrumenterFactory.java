/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.javaagent.instrumentation.netty.common.HttpRequestAndChannel;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectRequest;

public final class NettyClientInstrumenterFactory {

  private final String instrumentationName;
  private final boolean alwaysCreateConnectSpan;

  public NettyClientInstrumenterFactory(
      String instrumentationName, boolean alwaysCreateConnectSpan) {
    this.instrumentationName = instrumentationName;
    this.alwaysCreateConnectSpan = alwaysCreateConnectSpan;
  }

  public Instrumenter<HttpRequestAndChannel, HttpResponse> createHttpInstrumenter() {
    NettyHttpClientAttributesExtractor httpClientAttributesExtractor =
        new NettyHttpClientAttributesExtractor();
    NettyNetClientAttributesExtractor netClientAttributesExtractor =
        new NettyNetClientAttributesExtractor();

    return Instrumenter.<HttpRequestAndChannel, HttpResponse>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            HttpSpanNameExtractor.create(httpClientAttributesExtractor))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpClientAttributesExtractor))
        .addAttributesExtractor(httpClientAttributesExtractor)
        .addAttributesExtractor(netClientAttributesExtractor)
        .addAttributesExtractor(PeerServiceAttributesExtractor.create(netClientAttributesExtractor))
        .addRequestMetrics(HttpClientMetrics.get())
        .newClientInstrumenter(HttpRequestHeadersSetter.INSTANCE);
  }

  public NettyConnectInstrumenter createConnectInstrumenter() {
    NettyConnectNetAttributesExtractor netAttributesExtractor =
        new NettyConnectNetAttributesExtractor();
    Instrumenter<NettyConnectRequest, Channel> instrumenter =
        Instrumenter.<NettyConnectRequest, Channel>builder(
                GlobalOpenTelemetry.get(), instrumentationName, rq -> "CONNECT")
            .addAttributesExtractor(netAttributesExtractor)
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesExtractor))
            .setTimeExtractors(
                request -> request.timer().startTime(),
                (request, channel, error) -> request.timer().now())
            .newInstrumenter(
                alwaysCreateConnectSpan
                    ? SpanKindExtractor.alwaysInternal()
                    : SpanKindExtractor.alwaysClient());

    return alwaysCreateConnectSpan
        ? new NettyConnectInstrumenterImpl(instrumenter)
        : new NettyErrorOnlyConnectInstrumenter(instrumenter);
  }
}
