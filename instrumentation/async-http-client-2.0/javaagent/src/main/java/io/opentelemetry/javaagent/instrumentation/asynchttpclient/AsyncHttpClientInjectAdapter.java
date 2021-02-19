/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient;

import io.opentelemetry.context.propagation.TextMapPropagator;
import org.asynchttpclient.Request;

public class AsyncHttpClientInjectAdapter implements TextMapPropagator.Setter<Request> {

  public static final AsyncHttpClientInjectAdapter SETTER = new AsyncHttpClientInjectAdapter();

  @Override
  public void set(Request carrier, String key, String value) {
    carrier.getHeaders().set(key, value);
  }
}
