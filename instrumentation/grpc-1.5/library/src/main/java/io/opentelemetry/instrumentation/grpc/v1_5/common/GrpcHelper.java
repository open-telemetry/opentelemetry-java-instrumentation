/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_5.common;

import io.grpc.Status.Code;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Status.CanonicalCode;
import io.opentelemetry.trace.attributes.SemanticAttributes;

public final class GrpcHelper {
  public static void prepareSpan(Span span, String fullMethodName) {

    int slash = fullMethodName.indexOf('/');
    String serviceName = slash == -1 ? fullMethodName : fullMethodName.substring(0, slash);
    String methodName = slash == -1 ? null : fullMethodName.substring(slash + 1);

    span.setAttribute(SemanticAttributes.RPC_SERVICE, serviceName);
    span.setAttribute(SemanticAttributes.RPC_METHOD, methodName);
  }

  public static Status statusFromGrpcStatus(io.grpc.Status grpcStatus) {
    Status status = codeFromGrpcCode(grpcStatus.getCode()).toStatus();
    if (grpcStatus.getDescription() != null) {
      status = status.withDescription(grpcStatus.getDescription());
    }
    return status;
  }

  private static CanonicalCode codeFromGrpcCode(Code grpcCode) {
    return grpcCode.equals(Code.OK) ? CanonicalCode.UNSET : CanonicalCode.ERROR;
  }

  private GrpcHelper() {}
}
