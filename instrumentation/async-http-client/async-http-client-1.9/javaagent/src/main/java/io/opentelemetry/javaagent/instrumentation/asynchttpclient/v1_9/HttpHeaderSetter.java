/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_9;

import com.ning.http.client.Request;
import io.opentelemetry.context.propagation.TextMapSetter;

enum HttpHeaderSetter implements TextMapSetter<Request> {
  INSTANCE;

  @Override
  public void set(Request carrier, String key, String value) {
    carrier.getHeaders().replaceWith(key, value);
  }
}
