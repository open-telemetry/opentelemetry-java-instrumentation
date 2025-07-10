/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.common;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

final class ThriftAttributesExtractor implements AttributesExtractor<ThriftRequest, Integer> {

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, ThriftRequest request) {
    // Request attributes captured on request end.
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ThriftRequest request,
      @Nullable Integer status,
      @Nullable Throwable error) {}
}
