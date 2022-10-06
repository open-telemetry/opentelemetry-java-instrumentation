/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4.common.internal.client;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.netty.common.internal.HttpClientSpanKeyAttributesExtractor;
import io.opentelemetry.instrumentation.netty.common.internal.NettyConnectionRequest;
import io.opentelemetry.instrumentation.netty.v4.common.internal.HttpRequestAndChannel;
import java.util.List;
import java.util.Map;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class NettyClientInstrumenterFactory {

  private final OpenTelemetry openTelemetry;
  private final String instrumentationName;
  private final boolean connectionTelemetryEnabled;
  private final boolean sslTelemetryEnabled;
  private final List<String> capturedRequestHeaders;
  private final List<String> capturedResponseHeaders;
  private final Map<String, String> peerServiceMapping;

  public NettyClientInstrumenterFactory(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      boolean connectionTelemetryEnabled,
      boolean sslTelemetryEnabled,
      List<String> capturedRequestHeaders,
      List<String> capturedResponseHeaders,
      Map<String, String> peerServiceMapping) {
    this.openTelemetry = openTelemetry;
    this.instrumentationName = instrumentationName;
    this.connectionTelemetryEnabled = connectionTelemetryEnabled;
    this.sslTelemetryEnabled = sslTelemetryEnabled;
    this.capturedRequestHeaders = capturedRequestHeaders;
    this.capturedResponseHeaders = capturedResponseHeaders;
    this.peerServiceMapping = peerServiceMapping;
  }

  public Instrumenter<HttpRequestAndChannel, HttpResponse> createHttpInstrumenter() {
    NettyHttpClientAttributesGetter httpClientAttributesGetter =
        new NettyHttpClientAttributesGetter();
    NettyNetClientAttributesGetter netAttributesGetter = new NettyNetClientAttributesGetter();

    return Instrumenter.<HttpRequestAndChannel, HttpResponse>builder(
            openTelemetry,
            instrumentationName,
            HttpSpanNameExtractor.create(httpClientAttributesGetter))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpClientAttributesGetter))
        .addAttributesExtractor(
            HttpClientAttributesExtractor.builder(httpClientAttributesGetter)
                .setCapturedRequestHeaders(capturedRequestHeaders)
                .setCapturedResponseHeaders(capturedResponseHeaders)
                .build())
        .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
        .addAttributesExtractor(
            PeerServiceAttributesExtractor.create(netAttributesGetter, peerServiceMapping))
        .addOperationMetrics(HttpClientMetrics.get())
        .buildClientInstrumenter(HttpRequestHeadersSetter.INSTANCE);
  }

  public NettyConnectionInstrumenter createConnectionInstrumenter() {
    NettyConnectNetAttributesGetter netAttributesGetter = new NettyConnectNetAttributesGetter();

    InstrumenterBuilder<NettyConnectionRequest, Channel> instrumenterBuilder =
        Instrumenter.<NettyConnectionRequest, Channel>builder(
                openTelemetry, instrumentationName, NettyConnectionRequest::spanName)
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(netAttributesGetter, peerServiceMapping));
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
        instrumenterBuilder.buildInstrumenter(
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
                openTelemetry, instrumentationName, NettySslRequest::spanName)
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(netAttributesGetter, peerServiceMapping))
            .buildInstrumenter(
                sslTelemetryEnabled
                    ? SpanKindExtractor.alwaysInternal()
                    : SpanKindExtractor.alwaysClient());

    return sslTelemetryEnabled
        ? new NettySslInstrumenterImpl(instrumenter)
        : new NettySslErrorOnlyInstrumenter(instrumenter);
  }
}
