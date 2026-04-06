/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;
import software.amazon.awssdk.http.SdkHttpRequest;

class RequestHeaderSetter implements TextMapSetter<SdkHttpRequest.Builder> {

  @Override
  public void set(@Nullable SdkHttpRequest.Builder builder, String name, String value) {
    if (builder == null) {
      return;
    }
    builder.putHeader(name, value);
  }
}
