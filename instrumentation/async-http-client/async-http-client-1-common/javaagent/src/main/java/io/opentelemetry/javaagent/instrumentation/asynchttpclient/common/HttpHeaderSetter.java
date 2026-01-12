/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.common;

import com.ning.http.client.Request;
import io.opentelemetry.context.propagation.TextMapSetter;

final class HttpHeaderSetter implements TextMapSetter<Request> {

  private final AsyncHttpClientHelper helper;

  HttpHeaderSetter(AsyncHttpClientHelper helper) {
    this.helper = helper;
  }

  @Override
  public void set(Request carrier, String key, String value) {
    helper.setHeader(carrier, key, value);
  }
}
