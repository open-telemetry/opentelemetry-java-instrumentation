/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftRequest;
import javax.annotation.Nullable;

final class ThriftRequestGetter implements TextMapGetter<ThriftRequest> {

  @Override
  public Iterable<String> keys(ThriftRequest request) {
    return request.getHeaders().keySet();
  }

  @Override
  @Nullable
  public String get(@Nullable ThriftRequest request, String key) {
    if (request == null) {
      return null;
    }
    return request.getHeaders().get(key);
  }
}
