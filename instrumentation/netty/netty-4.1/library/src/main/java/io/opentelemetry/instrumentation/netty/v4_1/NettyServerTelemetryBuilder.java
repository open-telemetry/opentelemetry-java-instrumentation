/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.server.HttpRequestHeadersGetter;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.server.NettyHttpServerAttributesGetter;
import io.opentelemetry.instrumentation.netty.v4_1.internal.Experimental;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ProtocolEventHandler;
import io.opentelemetry.instrumentation.netty.v4_1.internal.server.NettyServerInstrumenterBuilderUtil;
import java.util.Collection;
import java.util.function.UnaryOperator;

/** Builder for {@link NettyServerTelemetry}. */
public final class NettyServerTelemetryBuilder {

  private final DefaultHttpServerInstrumenterBuilder<NettyRequest, HttpResponse> builder;

  private boolean emitExperimentalHttpServerEvents = false;

  static {
    NettyServerInstrumenterBuilderUtil.setBuilderExtractor(
        nettyServerTelemetryBuilder -> nettyServerTelemetryBuilder.builder);
    Experimental.internalSetEmitExperimentalServerTelemetry(
        (builder, emit) -> {
          builder.builder.setEmitExperimentalHttpServerTelemetry(emit);
          builder.emitExperimentalHttpServerEvents = emit;
        });
  }

  NettyServerTelemetryBuilder(OpenTelemetry openTelemetry) {
    builder =
        DefaultHttpServerInstrumenterBuilder.create(
            "io.opentelemetry.netty-4.1",
            openTelemetry,
            new NettyHttpServerAttributesGetter(),
            HttpRequestHeadersGetter.INSTANCE);
  }

  /**
   * Configures emission of experimental events.
   *
   * @param emitExperimentalHttpServerEvents set to true to emit events
   * @deprecated Use {@link Experimental#setEmitExperimentalTelemetry(NettyServerTelemetryBuilder,
   *     boolean)} instead.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public NettyServerTelemetryBuilder setEmitExperimentalHttpServerEvents(
      boolean emitExperimentalHttpServerEvents) {
    this.emitExperimentalHttpServerEvents = emitExperimentalHttpServerEvents;
    return this;
  }

  /**
   * Configures HTTP request headers to capture as span attributes.
   *
   * @param capturedRequestHeaders HTTP header names to capture.
   */
  @CanIgnoreReturnValue
  public NettyServerTelemetryBuilder setCapturedRequestHeaders(
      Collection<String> capturedRequestHeaders) {
    builder.setCapturedRequestHeaders(capturedRequestHeaders);
    return this;
  }

  /**
   * Configures HTTP response headers to capture as span attributes.
   *
   * @param capturedResponseHeaders HTTP header names to capture.
   */
  @CanIgnoreReturnValue
  public NettyServerTelemetryBuilder setCapturedResponseHeaders(
      Collection<String> capturedResponseHeaders) {
    builder.setCapturedResponseHeaders(capturedResponseHeaders);
    return this;
  }

  /**
   * Configures recognized HTTP request methods.
   *
   * <p>By default, recognizes methods from <a
   * href="https://www.rfc-editor.org/rfc/rfc9110.html#name-methods">RFC9110</a> and PATCH from <a
   * href="https://www.rfc-editor.org/rfc/rfc5789.html">RFC5789</a>.
   *
   * <p><b>Note:</b> This <b>overrides</b> defaults completely; it does not supplement them.
   *
   * @param knownMethods HTTP request methods to recognize.
   * @see HttpServerAttributesExtractorBuilder#setKnownMethods(Collection)
   */
  @CanIgnoreReturnValue
  public NettyServerTelemetryBuilder setKnownMethods(Collection<String> knownMethods) {
    builder.setKnownMethods(knownMethods);
    return this;
  }

  /** Customizes the {@link SpanNameExtractor} by transforming the default instance. */
  @CanIgnoreReturnValue
  public NettyServerTelemetryBuilder setSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<NettyRequest>> spanNameExtractorCustomizer) {
    builder.setSpanNameExtractorCustomizer(spanNameExtractorCustomizer);
    return this;
  }

  /** Returns a new instance with the configured settings. */
  public NettyServerTelemetry build() {
    return new NettyServerTelemetry(
        builder.build(),
        emitExperimentalHttpServerEvents
            ? ProtocolEventHandler.Enabled.INSTANCE
            : ProtocolEventHandler.Noop.INSTANCE);
  }
}
