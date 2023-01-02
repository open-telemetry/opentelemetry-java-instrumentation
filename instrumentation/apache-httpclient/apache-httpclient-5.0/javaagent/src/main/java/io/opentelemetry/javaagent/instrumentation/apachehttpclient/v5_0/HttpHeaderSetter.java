/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;

public enum HttpHeaderSetter implements TextMapSetter<ApacheHttpClientRequest> {
  INSTANCE;

  @Override
  public void set(@Nullable ApacheHttpClientRequest carrier, String key, String value) {
    if (carrier != null) {
      carrier.setHeader(key, value);
    }
  }
}
