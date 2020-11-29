/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import io.opentelemetry.context.propagation.TextMapPropagator;
import org.apache.http.HttpRequest;

public class HttpHeadersInjectAdapter implements TextMapPropagator.Setter<HttpRequest> {

  public static final HttpHeadersInjectAdapter SETTER = new HttpHeadersInjectAdapter();

  @Override
  public void set(HttpRequest carrier, String key, String value) {
    carrier.setHeader(key, value);
  }
}
