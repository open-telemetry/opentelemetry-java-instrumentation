/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.context.propagation.TextMapSetter;
import software.amazon.awssdk.http.SdkHttpRequest;

enum RequestHeaderSetter implements TextMapSetter<SdkHttpRequest.Builder> {
  INSTANCE;

  @Override
  public void set(SdkHttpRequest.Builder builder, String name, String value) {
    builder.appendHeader(name, value);
  }
}
