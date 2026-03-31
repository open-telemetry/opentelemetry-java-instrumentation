/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;
import org.apache.http.client.methods.HttpRequestWrapper;

class HttpHeaderSetter implements TextMapSetter<HttpRequestWrapper> {

  static final HttpHeaderSetter INSTANCE = new HttpHeaderSetter();

  private HttpHeaderSetter() {}

  @Override
  public void set(@Nullable HttpRequestWrapper carrier, String key, String value) {
    if (carrier != null) {
      carrier.setHeader(key, value);
    }
  }
}
