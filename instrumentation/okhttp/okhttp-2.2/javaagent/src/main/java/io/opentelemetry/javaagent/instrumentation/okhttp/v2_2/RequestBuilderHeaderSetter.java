/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import com.squareup.okhttp.Request;
import io.opentelemetry.context.propagation.TextMapSetter;

enum RequestBuilderHeaderSetter implements TextMapSetter<Request.Builder> {
  INSTANCE;

  @Override
  public void set(Request.Builder carrier, String key, String value) {
    carrier.header(key, value);
  }
}
