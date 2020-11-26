/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import com.amazonaws.Request;
import io.opentelemetry.context.propagation.TextMapPropagator;

final class AwsSdkInjectAdapter implements TextMapPropagator.Setter<Request<?>> {

  static final AwsSdkInjectAdapter INSTANCE = new AwsSdkInjectAdapter();

  @Override
  public void set(Request<?> request, String name, String value) {
    request.addHeader(name, value);
  }
}
