/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal;

import io.opentelemetry.context.propagation.TextMapSetter;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.http.HttpField;

enum HttpHeaderSetter implements TextMapSetter<Request> {
  INSTANCE;

  @Override
  public void set(Request request, String key, String value) {
    if (request != null) {
      request.headers(
          httpFields -> {
            httpFields.put(new HttpField(key, value));
          });
    }
  }
}
