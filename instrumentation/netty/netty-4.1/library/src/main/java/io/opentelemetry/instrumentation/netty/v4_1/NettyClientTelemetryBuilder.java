/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractorBuilder;
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyClientInstrumenterFactory;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyConnectionInstrumentationFlag;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ProtocolEventHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** A builder of {@link NettyClientTelemetry}. */
public final class NettyClientTelemetryBuilder {

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<HttpRequestAndChannel, HttpResponse>>
      additionalAttributesExtractors = new ArrayList<>();

  private Consumer<HttpClientAttributesExtractorBuilder<HttpRequestAndChannel, HttpResponse>>
      extractorConfigurer = builder -> {};
  private Consumer<HttpSpanNameExtractorBuilder<HttpRequestAndChannel>>
      spanNameExtractorConfigurer = builder -> {};
  private boolean emitExperimentalHttpClientMetrics = false;
  private NettyConnectionInstrumentationFlag connectionTelemetryState =
      NettyConnectionInstrumentationFlag.DISABLED;
  private NettyConnectionInstrumentationFlag sslTelemetryState =
      NettyConnectionInstrumentationFlag.DISABLED;

  private PeerServiceResolver peerServiceResolver =
      PeerServiceResolver.create(Collections.emptyMap());
  private boolean emitExperimentalHttpClientEvents = false;

  NettyClientTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @CanIgnoreReturnValue
  public NettyClientTelemetryBuilder setConnectionTelemetryState(
      NettyConnectionInstrumentationFlag connectionTelemetryState) {
    this.connectionTelemetryState = connectionTelemetryState;
    return this;
  }

  @CanIgnoreReturnValue
  public NettyClientTelemetryBuilder setSslTelemetryState(
      NettyConnectionInstrumentationFlag sslTelemetryState) {
    this.sslTelemetryState = sslTelemetryState;
    return this;
  }

  @CanIgnoreReturnValue
  public NettyClientTelemetryBuilder setPeerServiceResolver(
      PeerServiceResolver peerServiceResolver) {
    this.peerServiceResolver = peerServiceResolver;
    return this;
  }

  @CanIgnoreReturnValue
  public NettyClientTelemetryBuilder setEmitExperimentalHttpClientEvents(
      boolean emitExperimentalHttpClientEvents) {
    this.emitExperimentalHttpClientEvents = emitExperimentalHttpClientEvents;
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param capturedRequestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public NettyClientTelemetryBuilder setCapturedRequestHeaders(
      List<String> capturedRequestHeaders) {
    extractorConfigurer =
        extractorConfigurer.andThen(
            builder -> builder.setCapturedRequestHeaders(capturedRequestHeaders));
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param capturedResponseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public NettyClientTelemetryBuilder setCapturedResponseHeaders(
      List<String> capturedResponseHeaders) {
    extractorConfigurer =
        extractorConfigurer.andThen(
            builder -> builder.setCapturedResponseHeaders(capturedResponseHeaders));
    return this;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  @CanIgnoreReturnValue
  public NettyClientTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<HttpRequestAndChannel, HttpResponse> attributesExtractor) {
    additionalAttributesExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures the instrumentation to recognize an alternative set of HTTP request methods.
   *
   * <p>By default, this instrumentation defines "known" methods as the ones listed in <a
   * href="https://www.rfc-editor.org/rfc/rfc9110.html#name-methods">RFC9110</a> and the PATCH
   * method defined in <a href="https://www.rfc-editor.org/rfc/rfc5789.html">RFC5789</a>.
   *
   * <p>Note: calling this method <b>overrides</b> the default known method sets completely; it does
   * not supplement it.
   *
   * @param knownMethods A set of recognized HTTP request methods.
   * @see HttpClientAttributesExtractorBuilder#setKnownMethods(Set)
   */
  @CanIgnoreReturnValue
  public NettyClientTelemetryBuilder setKnownMethods(Set<String> knownMethods) {
    extractorConfigurer =
        extractorConfigurer.andThen(builder -> builder.setKnownMethods(knownMethods));
    spanNameExtractorConfigurer =
        spanNameExtractorConfigurer.andThen(builder -> builder.setKnownMethods(knownMethods));
    return this;
  }

  /**
   * Configures the instrumentation to emit experimental HTTP client metrics.
   *
   * @param emitExperimentalHttpClientMetrics {@code true} if the experimental HTTP client metrics
   *     are to be emitted.
   */
  @CanIgnoreReturnValue
  public NettyClientTelemetryBuilder setEmitExperimentalHttpClientMetrics(
      boolean emitExperimentalHttpClientMetrics) {
    this.emitExperimentalHttpClientMetrics = emitExperimentalHttpClientMetrics;
    return this;
  }

  /** Returns a new {@link NettyClientTelemetry} with the given configuration. */
  public NettyClientTelemetry build() {
    NettyClientInstrumenterFactory factory =
        new NettyClientInstrumenterFactory(
            openTelemetry,
            "io.opentelemetry.netty-4.1",
            connectionTelemetryState,
            sslTelemetryState,
            peerServiceResolver,
            emitExperimentalHttpClientMetrics);
    return new NettyClientTelemetry(
        factory.createHttpInstrumenter(
            extractorConfigurer, spanNameExtractorConfigurer, additionalAttributesExtractors),
        factory::createConnectionInstrumenter,
        factory::createSslInstrumenter,
        emitExperimentalHttpClientEvents
            ? ProtocolEventHandler.Enabled.INSTANCE
            : ProtocolEventHandler.Noop.INSTANCE);
  }
}
