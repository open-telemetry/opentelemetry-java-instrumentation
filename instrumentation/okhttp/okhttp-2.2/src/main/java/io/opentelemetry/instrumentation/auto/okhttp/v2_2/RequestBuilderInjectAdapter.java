/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.okhttp.v2_2;

import com.squareup.okhttp.Request;
import io.opentelemetry.context.propagation.TextMapPropagator;

public class RequestBuilderInjectAdapter implements TextMapPropagator.Setter<Request.Builder> {
  public static final RequestBuilderInjectAdapter SETTER = new RequestBuilderInjectAdapter();

  @Override
  public void set(Request.Builder carrier, String key, String value) {
    carrier.addHeader(key, value);
  }
}
