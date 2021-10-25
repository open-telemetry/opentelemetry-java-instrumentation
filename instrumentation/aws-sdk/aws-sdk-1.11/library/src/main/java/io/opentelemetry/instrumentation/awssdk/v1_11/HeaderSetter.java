/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.Request;
import io.opentelemetry.context.propagation.TextMapSetter;

enum HeaderSetter implements TextMapSetter<Request<?>> {
  INSTANCE;

  @Override
  public void set(Request<?> request, String name, String value) {
    request.addHeader(name, value);
  }
}
