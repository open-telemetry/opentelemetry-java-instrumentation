/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import io.opentelemetry.context.propagation.TextMapSetter;
import org.apache.http.client.methods.HttpUriRequest;
import org.checkerframework.checker.nullness.qual.Nullable;

final class HttpHeaderSetter implements TextMapSetter<HttpUriRequest> {

  static final HttpHeaderSetter INSTANCE = new HttpHeaderSetter();

  @Override
  public void set(@Nullable HttpUriRequest carrier, String key, String value) {
    if (carrier != null) {
      carrier.setHeader(key, value);
    }
  }
}
