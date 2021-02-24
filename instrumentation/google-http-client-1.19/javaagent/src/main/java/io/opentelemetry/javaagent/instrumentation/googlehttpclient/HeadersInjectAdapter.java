/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpHeaders;
import io.opentelemetry.context.propagation.TextMapSetter;

public class HeadersInjectAdapter implements TextMapSetter<HttpHeaders> {

  public static final HeadersInjectAdapter SETTER = new HeadersInjectAdapter();

  @Override
  public void set(HttpHeaders carrier, String key, String value) {
    carrier.put(key, value);
  }
}
