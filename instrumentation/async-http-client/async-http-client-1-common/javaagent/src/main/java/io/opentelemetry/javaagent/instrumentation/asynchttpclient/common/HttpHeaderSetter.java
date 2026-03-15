/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.common;

import com.ning.http.client.Request;
import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;

final class HttpHeaderSetter implements TextMapSetter<Request> {

  private final AsyncHttpClientHelper helper;

  HttpHeaderSetter(AsyncHttpClientHelper helper) {
    this.helper = helper;
  }

  @Override
  public void set(@Nullable Request carrier, String key, String value) {
    if (carrier == null) {
      return;
    }
    helper.setHeader(carrier, key, value);
  }
}
