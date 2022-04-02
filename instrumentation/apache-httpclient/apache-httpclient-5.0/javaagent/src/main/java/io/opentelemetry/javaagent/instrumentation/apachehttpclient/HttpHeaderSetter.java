/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.HttpRequest;

enum HttpHeaderSetter implements TextMapSetter<HttpRequest> {
  INSTANCE;

  @Override
  public void set(@Nullable HttpRequest carrier, String key, String value) {
    HttpUtils.setHeader(carrier, key, value);
  }
}
