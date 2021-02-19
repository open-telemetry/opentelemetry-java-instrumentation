/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.context.propagation.TextMapSetter;
import java.net.http.HttpRequest;

/** Context propagation is implemented via {@link HttpHeadersInstrumentation}. */
public class HttpHeadersInjectAdapter implements TextMapSetter<HttpRequest> {
  public static final HttpHeadersInjectAdapter SETTER = new HttpHeadersInjectAdapter();

  @Override
  public void set(HttpRequest carrier, String key, String value) {
    // Don't do anything because headers are immutable
  }
}
