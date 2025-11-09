/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.common;

import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.Map;
import javax.annotation.Nullable;

public enum ThriftHeaderSetter implements TextMapSetter<ThriftRequest> {
  INSTANCE;

  @Override
  public void set(@Nullable ThriftRequest request, String key, String value) {
    if (request == null) {
      return;
    }
    Map<String, String> header = request.getHeader();
    header.put(key, value);
  }
}
