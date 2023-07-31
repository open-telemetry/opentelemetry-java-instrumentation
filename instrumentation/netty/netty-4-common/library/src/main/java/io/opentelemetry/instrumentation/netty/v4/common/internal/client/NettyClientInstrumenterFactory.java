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
  private final boolean connectionTelemetryEnabled;
  private final boolean sslTelemetryEnabled;
  private final Map<String, String> peerServiceMapping;
  private final boolean emitExperimentalHttpClientMetrics;

  public NettyClientInstrumenterFactory(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      boolean connectionTelemetryEnabled,
      boolean sslTelemetryEnabled,
      Map<String, String> peerServiceMapping,
      boolean emitExperimentalHttpClientMetrics) {
    this.openTelemetry = openTelemetry;
    this.instrumentationName = instrumentationName;
    this.connectionTelemetryEnabled = connectionTelemetryEnabled;
    this.sslTelemetryEnabled = sslTelemetryEnabled;
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
    InstrumenterBuilder<NettyConnectionRequest, Channel> instrumenterBuilder =
        Instrumenter.<NettyConnectionRequest, Channel>builder(
                openTelemetry, instrumentationName, NettyConnectionRequest::spanName)
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(
                    NettyConnectHttpAttributesGetter.INSTANCE, peerServiceMapping));

    if (connectionTelemetryEnabled) {
      // TODO: this will most likely be no longer true with the new semconv, since the connection
      // phase happens *before* the actual HTTP request is sent over the wire
      // TODO (mateusz): refactor this after reactor-netty is fully converted to low-level HTTP
      // instrumentation
      // when the connection telemetry is enabled, we do not want these CONNECT spans to be
      // suppressed by higher-level HTTP spans - this would happen in the reactor-netty
      // instrumentation
      // the solution for this is to deliberately avoid using the HTTP extractor and use the plain
      // net attributes extractor instead
      instrumenterBuilder.addAttributesExtractor(
          NetClientAttributesExtractor.create(NettyConnectHttpAttributesGetter.INSTANCE));
    } else {
      // when the connection telemetry is not enabled, netty creates CONNECT spans whenever a
      // connection error occurs - because there is no HTTP span in that scenario, if raw netty
      // connection occurs before an HTTP message is even formed
      // we don't want that span when a higher-level HTTP library (like reactor-netty or async http
      // client) is used, the connection phase is a part of the HTTP span for these
      // for that to happen, the CONNECT span will "pretend" to be a full HTTP span when connection
      // telemetry is off
      instrumenterBuilder.addAttributesExtractor(
          HttpClientAttributesExtractor.create(NettyConnectHttpAttributesGetter.INSTANCE));
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
