/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.common;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import javax.annotation.Nullable;

public final class ThriftSpanStatusExtractor
    implements SpanStatusExtractor<ThriftRequest, Integer> {

  public static final ThriftSpanStatusExtractor INSTANCE = new ThriftSpanStatusExtractor();

  @Override
  public void extract(
      SpanStatusBuilder spanStatusBuilder,
      ThriftRequest request,
      @Nullable Integer status,
      @Nullable Throwable error) {
    if ((status != null && status > 0) || error != null) {
      spanStatusBuilder.setStatus(StatusCode.ERROR);
    }
  }
}
