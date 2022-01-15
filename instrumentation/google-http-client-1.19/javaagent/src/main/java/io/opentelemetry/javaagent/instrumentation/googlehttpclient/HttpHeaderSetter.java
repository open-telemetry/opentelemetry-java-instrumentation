/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import io.opentelemetry.context.propagation.TextMapSetter;

enum HttpHeaderSetter implements TextMapSetter<HttpRequest> {
  INSTANCE;

  @Override
  public void set(HttpRequest carrier, String key, String value) {
    carrier.getHeaders().set(key, value);
  }
}
