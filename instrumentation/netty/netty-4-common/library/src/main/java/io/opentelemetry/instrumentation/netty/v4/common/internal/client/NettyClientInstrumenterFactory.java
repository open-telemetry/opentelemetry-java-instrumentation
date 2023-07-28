/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4.common.internal.client;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientExperimentalMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.netty.common.internal.NettyConnectionRequest;
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class NettyClientInstrumenterFactory {

  private final OpenTelemetry openTelemetry;
  private final String instrumentationName;
  private final NettyConnectionInstrumentationFlag connectionTelemetryState;
  private final NettyConnectionInstrumentationFlag sslTelemetryState;
  private final Map<String, String> peerServiceMapping;
  private final boolean emitExperimentalHttpClientMetrics;

  public NettyClientInstrumenterFactory(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      NettyConnectionInstrumentationFlag connectionTelemetryState,
      NettyConnectionInstrumentationFlag sslTelemetryState,
      Map<String, String> peerServiceMapping,
      boolean emitExperimentalHttpClientMetrics) {
    this.openTelemetry = openTelemetry;
    this.instrumentationName = instrumentationName;
    this.connectionTelemetryState = connectionTelemetryState;
    this.sslTelemetryState = sslTelemetryState;
    this.peerServiceMapping = peerServiceMapping;
    this.emitExperimentalHttpClientMetrics = emitExperimentalHttpClientMetrics;
  }

  public Instrumenter<HttpRequestAndChannel, HttpResponse> createHttpInstrumenter(
      Consumer<HttpClientAttributesExtractorBuilder<HttpRequestAndChannel, HttpResponse>>
          extractorConfigurer,
      List<AttributesExtractor<HttpRequestAndChannel, HttpResponse>>
          additionalHttpAttributeExtractors) {
    NettyHttpClientAttributesGetter httpAttributesGetter = new NettyHttpClientAttributesGetter();

    HttpClientAttributesExtractorBuilder<HttpRequestAndChannel, HttpResponse> extractorBuilder =
        HttpClientAttributesExtractor.builder(httpAttributesGetter);
    extractorConfigurer.accept(extractorBuilder);

    InstrumenterBuilder<HttpRequestAndChannel, HttpResponse> builder =
        Instrumenter.<HttpRequestAndChannel, HttpResponse>builder(
                openTelemetry,
                instrumentationName,
                HttpSpanNameExtractor.create(httpAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(extractorBuilder.build())
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(httpAttributesGetter, peerServiceMapping))
            .addAttributesExtractors(additionalHttpAttributeExtractors)
            .addOperationMetrics(HttpClientMetrics.get());
    if (emitExperimentalHttpClientMetrics) {
      builder.addOperationMetrics(HttpClientExperimentalMetrics.get());
    }
    return builder.buildClientInstrumenter(HttpRequestHeadersSetter.INSTANCE);
  }

  public NettyConnectionInstrumenter createConnectionInstrumenter() {
    if (connectionTelemetryState == NettyConnectionInstrumentationFlag.DISABLED) {
      return NoopConnectionInstrumenter.INSTANCE;
    }

    boolean connectionTelemetryFullyEnabled =
        connectionTelemetryState == NettyConnectionInstrumentationFlag.ENABLED;

    Instrumenter<NettyConnectionRequest, Channel> instrumenter =
        Instrumenter.<NettyConnectionRequest, Channel>builder(
                openTelemetry, instrumentationName, NettyConnectionRequest::spanName)
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(
                    NettyConnectHttpAttributesGetter.INSTANCE, peerServiceMapping))
            .addAttributesExtractor(
                HttpClientAttributesExtractor.create(NettyConnectHttpAttributesGetter.INSTANCE))
            .buildInstrumenter(
                connectionTelemetryFullyEnabled
                    ? SpanKindExtractor.alwaysInternal()
                    : SpanKindExtractor.alwaysClient());

    return connectionTelemetryFullyEnabled
        ? new NettyConnectionInstrumenterImpl(instrumenter)
        : new NettyErrorOnlyConnectionInstrumenter(instrumenter);
  }

  public NettySslInstrumenter createSslInstrumenter() {
    if (sslTelemetryState == NettyConnectionInstrumentationFlag.DISABLED) {
      return NoopSslInstrumenter.INSTANCE;
    }

    boolean sslTelemetryFullyEnabled =
        sslTelemetryState == NettyConnectionInstrumentationFlag.ENABLED;
    NettySslNetAttributesGetter netAttributesGetter = new NettySslNetAttributesGetter();
    Instrumenter<NettySslRequest, Void> instrumenter =
        Instrumenter.<NettySslRequest, Void>builder(
                openTelemetry, instrumentationName, NettySslRequest::spanName)
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(netAttributesGetter, peerServiceMapping))
            .buildInstrumenter(
                sslTelemetryFullyEnabled
                    ? SpanKindExtractor.alwaysInternal()
                    : SpanKindExtractor.alwaysClient());

    return sslTelemetryFullyEnabled
        ? new NettySslInstrumenterImpl(instrumenter)
        : new NettySslErrorOnlyInstrumenter(instrumenter);
  }
}
