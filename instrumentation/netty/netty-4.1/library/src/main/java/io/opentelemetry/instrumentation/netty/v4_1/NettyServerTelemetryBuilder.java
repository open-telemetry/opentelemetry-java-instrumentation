/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;
import io.opentelemetry.instrumentation.netty.v4.common.internal.server.NettyServerInstrumenterFactory;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** A builder of {@link NettyServerTelemetry}. */
public final class NettyServerTelemetryBuilder {

  private final OpenTelemetry openTelemetry;
  private Consumer<HttpServerAttributesExtractorBuilder<HttpRequestAndChannel, HttpResponse>>
      extractorConfigurer = builder -> {};

  NettyServerTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param capturedRequestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public NettyServerTelemetryBuilder setCapturedRequestHeaders(
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
  public NettyServerTelemetryBuilder setCapturedResponseHeaders(
      List<String> capturedResponseHeaders) {
    extractorConfigurer =
        extractorConfigurer.andThen(
            builder -> builder.setCapturedResponseHeaders(capturedResponseHeaders));
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
   * @see HttpServerAttributesExtractorBuilder#setKnownMethods(Set)
   */
  @CanIgnoreReturnValue
  public NettyServerTelemetryBuilder setKnownMethods(Set<String> knownMethods) {
    extractorConfigurer =
        extractorConfigurer.andThen(builder -> builder.setKnownMethods(knownMethods));
    return this;
  }

  /** Returns a new {@link NettyServerTelemetry} with the given configuration. */
  public NettyServerTelemetry build() {
    return new NettyServerTelemetry(
        NettyServerInstrumenterFactory.create(
            openTelemetry, "io.opentelemetry.netty-4.1", extractorConfigurer));
  }
}
