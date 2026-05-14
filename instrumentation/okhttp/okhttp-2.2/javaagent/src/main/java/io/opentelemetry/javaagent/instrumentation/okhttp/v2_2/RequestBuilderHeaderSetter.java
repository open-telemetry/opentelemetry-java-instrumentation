/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import com.squareup.okhttp.Request;
import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;

enum RequestBuilderHeaderSetter implements TextMapSetter<Request.Builder> {
  INSTANCE;

  @Override
  public void set(@Nullable Request.Builder carrier, String key, String value) {
    if (carrier == null) {
      return;
    }
    carrier.header(key, value);
  }
}
