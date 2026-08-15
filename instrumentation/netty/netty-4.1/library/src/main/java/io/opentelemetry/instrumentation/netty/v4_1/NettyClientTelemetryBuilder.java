/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.NettyCommonRequest;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.client.NettyClientInstrumenterBuilderFactory;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.client.NettyClientInstrumenterFactory;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.client.NettyConnectionInstrumentationFlag;
import io.opentelemetry.instrumentation.netty.v4_1.internal.Experimental;
import java.util.Collection;
import java.util.function.UnaryOperator;

/** Builder for {@link NettyClientTelemetry}. */
public final class NettyClientTelemetryBuilder {

  private final DefaultHttpClientInstrumenterBuilder<NettyCommonRequest, HttpResponse> builder;
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
   * Configures which HTTP request headers are captured as span attributes.
   *
   * <p>Selector patterns are matched case-insensitively, since HTTP header names are
   * case-insensitive. {@code ?} matches one character and {@code *} matches any number of
   * characters, including none. Excluded patterns take precedence over included patterns. A
   * selector with no included patterns captures every header that is not excluded. No headers are
   * captured when no selector is configured or when the selector is {@linkplain
   * IncludeExclude#isEmpty() empty}.
   */
  @CanIgnoreReturnValue
  public NettyClientTelemetryBuilder setRequestHeaders(IncludeExclude requestHeaders) {
    builder.setRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures HTTP request headers to capture as span attributes.
   *
   * <p>The header names are matched literally. Unlike {@link #setRequestHeaders(IncludeExclude)},
   * {@code *} and {@code ?} are not treated as glob patterns, since this setting never supported
   * them as wildcards.
   *
   * @param capturedRequestHeaders HTTP header names to capture.
   * @deprecated Use {@link #setRequestHeaders(IncludeExclude)} instead, which matches glob patterns
   *     rather than literal header names. May be removed in the next minor release.
   */
  @Deprecated // may be removed in the next minor release
  @CanIgnoreReturnValue
  public NettyClientTelemetryBuilder setCapturedRequestHeaders(
      Collection<String> capturedRequestHeaders) {
    builder.setCapturedRequestHeaders(capturedRequestHeaders);
    return this;
  }

  /**
   * Configures which HTTP response headers are captured as span attributes.
   *
   * <p>Selector patterns are matched case-insensitively, since HTTP header names are
   * case-insensitive. {@code ?} matches one character and {@code *} matches any number of
   * characters, including none. Excluded patterns take precedence over included patterns. A
   * selector with no included patterns captures every header that is not excluded. No headers are
   * captured when no selector is configured or when the selector is {@linkplain
   * IncludeExclude#isEmpty() empty}.
   */
  @CanIgnoreReturnValue
  public NettyClientTelemetryBuilder setResponseHeaders(IncludeExclude responseHeaders) {
    builder.setResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Configures HTTP response headers to capture as span attributes.
   *
   * <p>The header names are matched literally. Unlike {@link #setResponseHeaders(IncludeExclude)},
   * {@code *} and {@code ?} are not treated as glob patterns, since this setting never supported
   * them as wildcards.
   *
   * @param capturedResponseHeaders HTTP header names to capture.
   * @deprecated Use {@link #setResponseHeaders(IncludeExclude)} instead, which matches glob
   *     patterns rather than literal header names. May be removed in the next minor release.
   */
  @Deprecated // may be removed in the next minor release
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
    builder.addAttributesExtractor(new AttributesExtractorAdapter(attributesExtractor));
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
    builder.setSpanNameExtractorCustomizer(
        InstrumenterUtil.convertSpanNameExtractor(
            spanNameExtractorCustomizer, NettyRequest::delegate, NettyRequest::create));
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
