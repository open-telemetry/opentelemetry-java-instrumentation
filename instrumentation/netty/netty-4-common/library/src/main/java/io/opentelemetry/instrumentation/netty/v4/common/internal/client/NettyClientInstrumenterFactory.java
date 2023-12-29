/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4.common.internal.client;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientExperimentalMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientPeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpExperimentalAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.netty.common.internal.NettyConnectionRequest;
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;
import java.util.List;
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
  private final PeerServiceResolver peerServiceResolver;
  private final boolean emitExperimentalHttpClientMetrics;

  public NettyClientInstrumenterFactory(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      NettyConnectionInstrumentationFlag connectionTelemetryState,
      NettyConnectionInstrumentationFlag sslTelemetryState,
      PeerServiceResolver peerServiceResolver,
      boolean emitExperimentalHttpClientMetrics) {
    this.openTelemetry = openTelemetry;
    this.instrumentationName = instrumentationName;
    this.connectionTelemetryState = connectionTelemetryState;
    this.sslTelemetryState = sslTelemetryState;
    this.peerServiceResolver = peerServiceResolver;
    this.emitExperimentalHttpClientMetrics = emitExperimentalHttpClientMetrics;
  }

  public Instrumenter<HttpRequestAndChannel, HttpResponse> createHttpInstrumenter(
      Consumer<HttpClientAttributesExtractorBuilder<HttpRequestAndChannel, HttpResponse>>
          extractorConfigurer,
      Consumer<HttpSpanNameExtractorBuilder<HttpRequestAndChannel>> spanNameExtractorConfigurer,
      List<AttributesExtractor<HttpRequestAndChannel, HttpResponse>>
          additionalHttpAttributeExtractors) {
    NettyHttpClientAttributesGetter httpAttributesGetter = new NettyHttpClientAttributesGetter();

    HttpClientAttributesExtractorBuilder<HttpRequestAndChannel, HttpResponse> extractorBuilder =
        HttpClientAttributesExtractor.builder(httpAttributesGetter);
    extractorConfigurer.accept(extractorBuilder);

    HttpSpanNameExtractorBuilder<HttpRequestAndChannel> httpSpanNameExtractorBuilder =
        HttpSpanNameExtractor.builder(httpAttributesGetter);
    spanNameExtractorConfigurer.accept(httpSpanNameExtractorBuilder);

    InstrumenterBuilder<HttpRequestAndChannel, HttpResponse> builder =
        Instrumenter.<HttpRequestAndChannel, HttpResponse>builder(
                openTelemetry, instrumentationName, httpSpanNameExtractorBuilder.build())
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(extractorBuilder.build())
            .addAttributesExtractor(
                HttpClientPeerServiceAttributesExtractor.create(
                    httpAttributesGetter, peerServiceResolver))
            .addAttributesExtractors(additionalHttpAttributeExtractors)
            .addOperationMetrics(HttpClientMetrics.get());
    if (emitExperimentalHttpClientMetrics) {
      builder
          .addAttributesExtractor(HttpExperimentalAttributesExtractor.create(httpAttributesGetter))
          .addOperationMetrics(HttpClientExperimentalMetrics.get());
    }
    return builder.buildClientInstrumenter(HttpRequestHeadersSetter.INSTANCE);
  }

  public NettyConnectionInstrumenter createConnectionInstrumenter() {
    if (connectionTelemetryState == NettyConnectionInstrumentationFlag.DISABLED) {
      return NoopConnectionInstrumenter.INSTANCE;
    }

    boolean connectionTelemetryFullyEnabled =
        connectionTelemetryState == NettyConnectionInstrumentationFlag.ENABLED;
    NettyConnectHttpAttributesGetter getter = NettyConnectHttpAttributesGetter.INSTANCE;

    InstrumenterBuilder<NettyConnectionRequest, Channel> builder =
        Instrumenter.<NettyConnectionRequest, Channel>builder(
                openTelemetry, instrumentationName, NettyConnectionRequest::spanName)
            .addAttributesExtractor(
                HttpClientPeerServiceAttributesExtractor.create(getter, peerServiceResolver));

    if (connectionTelemetryFullyEnabled) {
      // when the connection telemetry is fully enabled, CONNECT spans are created for every
      // request; and semantically they're not HTTP spans, they must not use the HTTP client
      // extractor
      builder.addAttributesExtractor(NetworkAttributesExtractor.create(getter));
      builder.addAttributesExtractor(ServerAttributesExtractor.create(getter));
    } else {
      // in case the connection telemetry is emitted only on errors, the CONNECT span is a stand-in
      // for the HTTP client span
      builder.addAttributesExtractor(HttpClientAttributesExtractor.create(getter));
    }

    Instrumenter<NettyConnectionRequest, Channel> instrumenter =
        builder.buildInstrumenter(
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
            .addAttributesExtractor(NetworkAttributesExtractor.create(netAttributesGetter))
            .buildInstrumenter(
                sslTelemetryFullyEnabled
                    ? SpanKindExtractor.alwaysInternal()
                    : SpanKindExtractor.alwaysClient());

    return sslTelemetryFullyEnabled
        ? new NettySslInstrumenterImpl(instrumenter)
        : new NettySslErrorOnlyInstrumenter(instrumenter);
  }
}
