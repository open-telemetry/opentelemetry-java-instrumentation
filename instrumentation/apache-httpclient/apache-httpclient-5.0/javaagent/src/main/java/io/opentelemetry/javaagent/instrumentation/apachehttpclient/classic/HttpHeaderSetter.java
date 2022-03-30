/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.classic;

import io.opentelemetry.context.propagation.TextMapSetter;
import org.apache.hc.core5.http.ClassicHttpRequest;

enum HttpHeaderSetter implements TextMapSetter<ClassicHttpRequest> {
  INSTANCE;

  @Override
  public void set(ClassicHttpRequest carrier, String key, String value) {
    carrier.setHeader(key, value);
  }
}
