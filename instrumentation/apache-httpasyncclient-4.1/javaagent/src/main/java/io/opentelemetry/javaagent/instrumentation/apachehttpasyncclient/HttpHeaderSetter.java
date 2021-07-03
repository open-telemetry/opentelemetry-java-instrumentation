/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import io.opentelemetry.context.propagation.TextMapSetter;

public class HttpHeaderSetter implements TextMapSetter<ApacheHttpClientRequest> {

  public static final HttpHeaderSetter SETTER = new HttpHeaderSetter();

  @Override
  public void set(ApacheHttpClientRequest carrier, String key, String value) {
    carrier.setHeader(key, value);
  }
}
