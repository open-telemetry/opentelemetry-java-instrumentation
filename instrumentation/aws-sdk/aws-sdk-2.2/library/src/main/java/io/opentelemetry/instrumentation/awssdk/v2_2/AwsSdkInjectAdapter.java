/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.context.propagation.TextMapPropagator;
import software.amazon.awssdk.http.SdkHttpRequest;

final class AwsSdkInjectAdapter implements TextMapPropagator.Setter<SdkHttpRequest.Builder> {

  static final AwsSdkInjectAdapter INSTANCE = new AwsSdkInjectAdapter();

  @Override
  public void set(SdkHttpRequest.Builder builder, String name, String value) {
    builder.appendHeader(name, value);
  }
}
