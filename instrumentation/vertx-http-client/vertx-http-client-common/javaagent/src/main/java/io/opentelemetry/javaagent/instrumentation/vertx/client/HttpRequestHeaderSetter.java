/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.client;

import io.opentelemetry.context.propagation.TextMapSetter;
import io.vertx.core.http.HttpClientRequest;

public class HttpRequestHeaderSetter implements TextMapSetter<HttpClientRequest> {
  static final HttpRequestHeaderSetter INSTANCE = new HttpRequestHeaderSetter();

  @Override
  public void set(HttpClientRequest carrier, String key, String value) {
    if (carrier != null) {
      carrier.putHeader(key, value);
    }
  }
}
