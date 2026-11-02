/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import com.alipay.sofa.rpc.core.response.SofaResponse;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import javax.annotation.Nullable;

final class SofaRpcSpanStatusExtractor
    implements SpanStatusExtractor<SofaRpcRequest, SofaResponse> {

  @Override
  public void extract(
      SpanStatusBuilder spanStatusBuilder,
      SofaRpcRequest request,
      @Nullable SofaResponse response,
      @Nullable Throwable error) {
    if (error != null || SofaRpcResponseUtil.isError(response)) {
      spanStatusBuilder.setStatus(StatusCode.ERROR);
    }
  }
}
