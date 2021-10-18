/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import javax.annotation.Nullable;

final class GrpcSpanStatusExtractor implements SpanStatusExtractor<GrpcRequest, Status> {
  @Override
  public StatusCode extract(GrpcRequest request, Status status, @Nullable Throwable error) {
    if (status == null) {
      if (error instanceof StatusRuntimeException) {
        status = ((StatusRuntimeException) error).getStatus();
      } else if (error instanceof StatusException) {
        status = ((StatusException) error).getStatus();
      }
    }
    if (status != null) {
      if (status.isOk()) {
        return StatusCode.UNSET;
      }
      return StatusCode.ERROR;
    }
    return SpanStatusExtractor.getDefault().extract(request, status, error);
  }
}
