/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.common;

import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;

public enum ThriftHeaderGetter implements TextMapGetter<ThriftRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(ThriftRequest request) {
    return request.getHeader().keySet();
  }

  @Override
  @Nullable
  public String get(@Nullable ThriftRequest request, String key) {
    if (request == null) {
      return null;
    }
    return request.getHeader().get(key);
  }
}
