/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyClientInstrumenterFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A builder of {@link NettyClientTelemetry}. */
public final class NettyClientTelemetryBuilder {

  private final OpenTelemetry openTelemetry;
  private List<String> capturedRequestHeaders = Collections.emptyList();
  private List<String> capturedResponseHeaders = Collections.emptyList();
  private final List<AttributesExtractor<HttpRequestAndChannel, HttpResponse>>
      additionalAttributesExtractors = new ArrayList<>();

  NettyClientTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param capturedRequestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public NettyClientTelemetryBuilder setCapturedRequestHeaders(
      List<String> capturedRequestHeaders) {
    this.capturedRequestHeaders = capturedRequestHeaders;
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
    this.capturedResponseHeaders = capturedResponseHeaders;
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

  /** Returns a new {@link NettyClientTelemetry} with the given configuration. */
  public NettyClientTelemetry build() {
    return new NettyClientTelemetry(
        new NettyClientInstrumenterFactory(
                openTelemetry, "io.opentelemetry.netty-4.1", false, false, Collections.emptyMap())
            .createHttpInstrumenter(
                capturedRequestHeaders, capturedResponseHeaders, additionalAttributesExtractors));
  }
}
