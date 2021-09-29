/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.config;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders;

/** Javaagent configuration of captured HTTP request/response headers. */
public final class HttpHeadersConfig {

  private static final CapturedHttpHeaders CLIENT;
  private static final CapturedHttpHeaders SERVER;

  static {
    Config config = Config.get();
    CLIENT =
        CapturedHttpHeaders.create(
            config.getList(
                "otel.instrumentation.common.experimental.capture-http-headers.client.request"),
            config.getList(
                "otel.instrumentation.common.experimental.capture-http-headers.client.response"));
    SERVER =
        CapturedHttpHeaders.create(
            config.getList(
                "otel.instrumentation.common.experimental.capture-http-headers.server.request"),
            config.getList(
                "otel.instrumentation.common.experimental.capture-http-headers.server.response"));
  }

  public static CapturedHttpHeaders capturedClientHeaders() {
    return CLIENT;
  }

  public static CapturedHttpHeaders capturedServerHeaders() {
    return SERVER;
  }

  private HttpHeadersConfig() {}
}
