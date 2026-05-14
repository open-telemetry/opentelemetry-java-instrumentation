/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.Request;
import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;

class HeaderSetter implements TextMapSetter<Request<?>> {

  @Override
  public void set(@Nullable Request<?> request, String name, String value) {
    if (request == null) {
      return;
    }
    request.addHeader(name, value);
  }
}
