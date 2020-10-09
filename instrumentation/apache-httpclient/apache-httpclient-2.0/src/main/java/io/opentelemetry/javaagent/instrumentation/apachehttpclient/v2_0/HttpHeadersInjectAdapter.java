/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.apachehttpclient.v2_0;

import io.opentelemetry.context.propagation.TextMapPropagator;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpMethod;

public class HttpHeadersInjectAdapter implements TextMapPropagator.Setter<HttpMethod> {

  public static final HttpHeadersInjectAdapter SETTER = new HttpHeadersInjectAdapter();

  @Override
  public void set(HttpMethod carrier, String key, String value) {
    carrier.setRequestHeader(new Header(key, value));
  }
}
