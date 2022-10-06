/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyClientInstrumenterFactory;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** A builder of {@link NettyClientTelemetry}. */
public final class NettyClientTelemetryBuilder {

  private final OpenTelemetry openTelemetry;
  private List<String> capturedRequestHeaders = Collections.emptyList();
  private List<String> capturedResponseHeaders = Collections.emptyList();
  private Map<String, String> peerServiceMapping = Collections.emptyMap();

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
   * Configures the mapping between peer names and peer services used when capturing peer service
   * span attributes.
   *
   * @param peerServiceMapping A map of peer names to peer services.
   */
  @CanIgnoreReturnValue
  public NettyClientTelemetryBuilder setPeerServiceMapping(Map<String, String> peerServiceMapping) {
    this.peerServiceMapping = peerServiceMapping;
    return this;
  }

  /** Returns a new {@link NettyClientTelemetry} with the given configuration. */
  public NettyClientTelemetry build() {
    return new NettyClientTelemetry(
        new NettyClientInstrumenterFactory(
            openTelemetry,
            "io.opentelemetry.netty-4.1",
            false,
            false,
            capturedRequestHeaders,
            capturedResponseHeaders,
            peerServiceMapping));
  }
}
