/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import io.opentelemetry.context.propagation.TextMapSetter;

enum HttpHeaderSetter implements TextMapSetter<ApacheHttpClientRequest> {
  INSTANCE;

  @Override
  public void set(ApacheHttpClientRequest carrier, String key, String value) {
    carrier.setHeader(key, value);
  }
}
