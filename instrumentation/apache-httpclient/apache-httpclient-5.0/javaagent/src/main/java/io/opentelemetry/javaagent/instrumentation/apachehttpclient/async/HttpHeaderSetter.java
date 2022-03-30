/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.async;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;

enum HttpHeaderSetter implements TextMapSetter<ApacheHttpClientRequest> {
  INSTANCE;

  @Override
  public void set(@Nullable ApacheHttpClientRequest carrier, String key, String value) {
    if (carrier == null) {
      return;
    }
    carrier.setHeader(key, value);
  }
}
