/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4.common.client;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectionRequest;
import io.opentelemetry.javaagent.instrumentation.netty.v4.common.HttpRequestAndChannel;

public final class NettyClientInstrumenterFactory {

  private final String instrumentationName;
  private final boolean connectionTelemetryEnabled;
  private final boolean sslTelemetryEnabled;

  public NettyClientInstrumenterFactory(
      String instrumentationName, boolean connectionTelemetryEnabled, boolean sslTelemetryEnabled) {
    this.instrumentationName = instrumentationName;
    this.connectionTelemetryEnabled = connectionTelemetryEnabled;
    this.sslTelemetryEnabled = sslTelemetryEnabled;
  }

  public Instrumenter<HttpRequestAndChannel, HttpResponse> createHttpInstrumenter() {
    NettyHttpClientAttributesGetter httpClientAttributesGetter =
        new NettyHttpClientAttributesGetter();
    NettyNetClientAttributesGetter netAttributesGetter = new NettyNetClientAttributesGetter();

    return Instrumenter.<HttpRequestAndChannel, HttpResponse>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            HttpSpanNameExtractor.create(httpClientAttributesGetter))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpClientAttributesGetter))
        .addAttributesExtractor(HttpClientAttributesExtractor.create(httpClientAttributesGetter))
        .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
        .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesGetter))
        .addOperationMetrics(HttpClientMetrics.get())
        .newClientInstrumenter(HttpRequestHeadersSetter.INSTANCE);
  }

  public NettyConnectionInstrumenter createConnectionInstrumenter() {
    NettyConnectNetAttributesGetter netAttributesGetter = new NettyConnectNetAttributesGetter();

    InstrumenterBuilder<NettyConnectionRequest, Channel> instrumenterBuilder =
        Instrumenter.<NettyConnectionRequest, Channel>builder(
                GlobalOpenTelemetry.get(), instrumentationName, NettyConnectionRequest::spanName)
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesGetter))
            .setTimeExtractor(new NettyConnectionTimeExtractor());
    if (!connectionTelemetryEnabled) {
      // when the connection telemetry is not enabled, netty creates CONNECT spans whenever a
      // connection error occurs - because there is no HTTP span in that scenario, if raw netty
      // connection occurs before an HTTP message is even formed
      // we don't want that span when a higher-level HTTP library (like reactor-netty or async http
      // client) is used, the connection phase is a part of the HTTP span for these
      // for that to happen, the CONNECT span will "pretend" to be a full HTTP span when connection
      // telemetry is off
      instrumenterBuilder.addAttributesExtractor(HttpClientSpanKeyAttributesExtractor.INSTANCE);
    }

    Instrumenter<NettyConnectionRequest, Channel> instrumenter =
        instrumenterBuilder.newInstrumenter(
            connectionTelemetryEnabled
                ? SpanKindExtractor.alwaysInternal()
                : SpanKindExtractor.alwaysClient());

    return connectionTelemetryEnabled
        ? new NettyConnectionInstrumenterImpl(instrumenter)
        : new NettyErrorOnlyConnectionInstrumenter(instrumenter);
  }

  public NettySslInstrumenter createSslInstrumenter() {
    NettySslNetAttributesGetter netAttributesGetter = new NettySslNetAttributesGetter();
    Instrumenter<NettySslRequest, Void> instrumenter =
        Instrumenter.<NettySslRequest, Void>builder(
                GlobalOpenTelemetry.get(), instrumentationName, NettySslRequest::spanName)
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesGetter))
            .setTimeExtractor(new NettySslTimeExtractor())
            .newInstrumenter(
                sslTelemetryEnabled
                    ? SpanKindExtractor.alwaysInternal()
                    : SpanKindExtractor.alwaysClient());

    return sslTelemetryEnabled
        ? new NettySslInstrumenterImpl(instrumenter)
        : new NettySslErrorOnlyInstrumenter(instrumenter);
  }
}
