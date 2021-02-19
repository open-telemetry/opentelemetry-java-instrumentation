/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import com.squareup.okhttp.Request;
import io.opentelemetry.context.propagation.TextMapSetter;

public class RequestBuilderInjectAdapter implements TextMapSetter<Request.Builder> {
  public static final RequestBuilderInjectAdapter SETTER = new RequestBuilderInjectAdapter();

  @Override
  public void set(Request.Builder carrier, String key, String value) {
    carrier.addHeader(key, value);
  }
}
