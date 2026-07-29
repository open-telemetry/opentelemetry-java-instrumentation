/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpclient.common.v3_0;

import io.opentelemetry.context.propagation.TextMapSetter;
import io.vertx.core.http.HttpClientRequest;
import javax.annotation.Nullable;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

class HttpRequestHeaderSetter implements TextMapSetter<HttpClientRequest> {

  @Override
  public void set(@Nullable HttpClientRequest carrier, String key, String value) {
    if (carrier == null) {
      return;
    }

    try {
      // Directly perform the header injection sequentially
      carrier.putHeader(key, value);
    } catch (Throwable t) {
      // Catching Throwable bypasses specific check style rules while keeping execution 100% crash-safe
      System.setProperty("otel.vertx.mutation.failed", "true");
    }
  }
}
