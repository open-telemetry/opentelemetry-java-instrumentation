/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Result;

final class DubboRpcSpanStatusExtractor implements SpanStatusExtractor<DubboRequest, Result> {

  private final boolean isClient;

  DubboRpcSpanStatusExtractor(boolean isClient) {
    this.isClient = isClient;
  }

  @Override
  public void extract(
      SpanStatusBuilder spanStatusBuilder,
      DubboRequest request,
      @Nullable Result response,
      @Nullable Throwable error) {
    String statusCode = DubboRpcStatus.getStatusCode(response, error);
    if (statusCode == null || "OK".equals(statusCode)) {
      SpanStatusExtractor.<DubboRequest, Result>getDefault()
          .extract(spanStatusBuilder, request, response, error);
    } else if (DubboRpcStatus.isError(statusCode, isClient)) {
      spanStatusBuilder.setStatus(StatusCode.ERROR);
    } else {
      spanStatusBuilder.setStatus(StatusCode.UNSET);
    }
  }
}
