/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.client.NettyClientInstrumenterBuilderFactory;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.client.NettyClientInstrumenterFactory;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.client.NettyConnectionInstrumentationFlag;
import io.opentelemetry.instrumentation.netty.v4_1.internal.Experimental;
import java.util.Collection;
import java.util.function.UnaryOperator;

/** Builder for {@link NettyClientTelemetry}. */
public final class NettyClientTelemetryBuilder {

  private final DefaultHttpClientInstrumenterBuilder<NettyRequest, HttpResponse> builder;
  private boolean emitExperimentalHttpClientEvents = false;

  static {
    Experimental.internalSetEmitExperimentalClientTelemetry(
        (builder, emit) -> {
          builder.builder.setEmitExperimentalHttpClientTelemetry(emit);
          builder.emitExperimentalHttpClientEvents = emit;
        });
  }

  NettyClientTelemetryBuilder(OpenTelemetry openTelemetry) {
    builder =
        NettyClientInstrumenterBuilderFactory.create("io.opentelemetry.netty-4.1", openTelemetry);
  }

  /**
   * Configures HTTP request headers to capture as span attributes.
   *
   * @param capturedRequestHeaders HTTP header names to capture.
   */
  @CanIgnoreReturnValue
  public NettyClientTelemetryBuilder setCapturedRequestHeaders(
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
  public NettyClientTelemetryBuilder setCapturedResponseHeaders(
      Collection<String> capturedResponseHeaders) {
    builder.setCapturedResponseHeaders(capturedResponseHeaders);
    return this;
  }

  /**
   * Adds an {@link AttributesExtractor} to extract attributes from requests and responses. Executed
   * after all default extractors.
   */
  @CanIgnoreReturnValue
  public NettyClientTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<NettyRequest, HttpResponse> attributesExtractor) {
    builder.addAttributesExtractor(attributesExtractor);
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
   * @see HttpClientAttributesExtractorBuilder#setKnownMethods(Collection)
   */
  @CanIgnoreReturnValue
  public NettyClientTelemetryBuilder setKnownMethods(Collection<String> knownMethods) {
    builder.setKnownMethods(knownMethods);
    return this;
  }

  /** Customizes the {@link SpanNameExtractor} by transforming the default instance. */
  @CanIgnoreReturnValue
  public NettyClientTelemetryBuilder setSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<NettyRequest>> spanNameExtractorCustomizer) {
    builder.setSpanNameExtractorCustomizer(spanNameExtractorCustomizer);
    return this;
  }

  /** Returns a new instance with the configured settings. */
  public NettyClientTelemetry build() {
    return new NettyClientTelemetry(
        new NettyClientInstrumenterFactory(
                builder,
                NettyConnectionInstrumentationFlag.DISABLED,
                NettyConnectionInstrumentationFlag.DISABLED)
            .instrumenter(),
        emitExperimentalHttpClientEvents);
  }
}
