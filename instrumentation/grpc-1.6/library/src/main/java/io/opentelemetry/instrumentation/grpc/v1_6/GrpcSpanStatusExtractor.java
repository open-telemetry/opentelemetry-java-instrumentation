/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import com.google.errorprone.annotations.Immutable;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;

enum GrpcSpanStatusExtractor implements SpanStatusExtractor<GrpcRequest, Status> {
  CLIENT(GrpcSpanStatusExtractor::isClientError),
  SERVER(GrpcSpanStatusExtractor::isServerError);

  private final ErrorStatusPredicate isError;

  GrpcSpanStatusExtractor(ErrorStatusPredicate isError) {
    this.isError = isError;
  }

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
      if (isError.test(status)) {
        spanStatusBuilder.setStatus(StatusCode.ERROR);
      }
    } else {
      SpanStatusExtractor.getDefault().extract(spanStatusBuilder, request, status, error);
    }
  }

  private static final Set<Status.Code> serverErrorStatuses = new HashSet<>();

  static {
    serverErrorStatuses.addAll(
        Arrays.asList(
            Status.Code.UNKNOWN,
            Status.Code.DEADLINE_EXCEEDED,
            Status.Code.UNIMPLEMENTED,
            Status.Code.INTERNAL,
            Status.Code.UNAVAILABLE,
            Status.Code.DATA_LOSS));
  }

  private static boolean isServerError(Status status) {
    return serverErrorStatuses.contains(status.getCode());
  }

  private static boolean isClientError(Status status) {
    return !status.isOk();
  }

  @Immutable
  private interface ErrorStatusPredicate extends Predicate<Status> {}
}
