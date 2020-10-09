/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.httpurlconnection;

import io.opentelemetry.context.propagation.TextMapPropagator;
import java.net.HttpURLConnection;

public class HeadersInjectAdapter implements TextMapPropagator.Setter<HttpURLConnection> {

  public static final HeadersInjectAdapter SETTER = new HeadersInjectAdapter();

  @Override
  public void set(HttpURLConnection carrier, String key, String value) {
    carrier.setRequestProperty(key, value);
  }
}
