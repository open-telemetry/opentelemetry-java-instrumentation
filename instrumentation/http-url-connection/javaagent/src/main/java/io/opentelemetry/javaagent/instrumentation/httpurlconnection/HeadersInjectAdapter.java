/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import io.opentelemetry.context.propagation.TextMapSetter;
import java.net.HttpURLConnection;

public class HeadersInjectAdapter implements TextMapSetter<HttpURLConnection> {

  public static final HeadersInjectAdapter SETTER = new HeadersInjectAdapter();

  @Override
  public void set(HttpURLConnection carrier, String key, String value) {
    carrier.setRequestProperty(key, value);
  }
}
