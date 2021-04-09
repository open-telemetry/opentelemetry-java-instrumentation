/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient;

import com.ning.http.client.Request;
import io.opentelemetry.context.propagation.TextMapSetter;

public class AsyncHttpClientInjectAdapter implements TextMapSetter<Request> {

  public static final AsyncHttpClientInjectAdapter SETTER = new AsyncHttpClientInjectAdapter();

  @Override
  public void set(Request carrier, String key, String value) {
    carrier.getHeaders().replaceWith(key, value);
  }
}
