/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import javax.annotation.Nullable;

final class GrpcSpanStatusExtractor implements SpanStatusExtractor<GrpcRequest, Status> {
  @Override
  public void extract(
      SpanStatusBuilder spanStatusBuilder,
      GrpcRequest request,
      Status status,
      @Nullable Throwable error) {
    if (status == null) {
      if (error instanceof StatusRuntimeException) {
        status = ((StatusRuntimeException) error).getStatus();
      } else if (error instanceof StatusException) {
        status = ((StatusException) error).getStatus();
      }
    }
    if (status != null) {
      if (status.isOk()) {
        spanStatusBuilder.setStatus(StatusCode.UNSET);
      } else {
        spanStatusBuilder.setStatus(StatusCode.ERROR);
      }
    } else {
      SpanStatusExtractor.getDefault().extract(spanStatusBuilder, request, status, error);
    }
  }
}
