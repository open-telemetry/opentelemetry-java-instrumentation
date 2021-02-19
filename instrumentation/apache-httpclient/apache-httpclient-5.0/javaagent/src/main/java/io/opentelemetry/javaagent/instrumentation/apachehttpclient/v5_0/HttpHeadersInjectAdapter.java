/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.context.propagation.TextMapSetter;
import org.apache.hc.core5.http.ClassicHttpRequest;

class HttpHeadersInjectAdapter implements TextMapSetter<ClassicHttpRequest> {

  public static final HttpHeadersInjectAdapter SETTER = new HttpHeadersInjectAdapter();

  @Override
  public void set(ClassicHttpRequest carrier, String key, String value) {
    carrier.addHeader(key, value);
  }
}
