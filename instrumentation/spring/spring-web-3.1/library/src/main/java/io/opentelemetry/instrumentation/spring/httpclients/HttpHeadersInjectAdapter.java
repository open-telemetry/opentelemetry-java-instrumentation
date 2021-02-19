/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.httpclients;

import io.opentelemetry.context.propagation.TextMapSetter;
import org.springframework.http.HttpHeaders;

class HttpHeadersInjectAdapter implements TextMapSetter<HttpHeaders> {

  public static final HttpHeadersInjectAdapter SETTER = new HttpHeadersInjectAdapter();

  @Override
  public void set(HttpHeaders carrier, String key, String value) {
    carrier.set(key, value);
  }
}
