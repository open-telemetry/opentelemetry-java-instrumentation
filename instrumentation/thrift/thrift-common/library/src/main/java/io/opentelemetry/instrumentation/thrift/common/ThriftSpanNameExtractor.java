/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.common;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public final class ThriftSpanNameExtractor implements SpanNameExtractor<ThriftRequest> {
  @Override
  public String extract(ThriftRequest request) {
    return request.getMethodName();
  }
}
