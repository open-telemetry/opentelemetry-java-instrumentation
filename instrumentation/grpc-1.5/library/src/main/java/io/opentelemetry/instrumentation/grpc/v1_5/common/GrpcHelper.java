/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_5.common;

import io.grpc.Status.Code;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.StatusCanonicalCode;
import io.opentelemetry.trace.attributes.SemanticAttributes;

public final class GrpcHelper {
  public static void prepareSpan(Span span, String fullMethodName) {

    int slash = fullMethodName.indexOf('/');
    String serviceName = slash == -1 ? fullMethodName : fullMethodName.substring(0, slash);
    String methodName = slash == -1 ? null : fullMethodName.substring(slash + 1);

    span.setAttribute(SemanticAttributes.RPC_SERVICE, serviceName);
    if (methodName != null) {
      span.setAttribute(SemanticAttributes.RPC_METHOD, methodName);
    }
  }

  public static StatusCanonicalCode statusFromGrpcStatus(io.grpc.Status grpcStatus) {
    return codeFromGrpcCode(grpcStatus.getCode());
  }

  private static StatusCanonicalCode codeFromGrpcCode(Code grpcCode) {
    return grpcCode.equals(Code.OK) ? StatusCanonicalCode.UNSET : StatusCanonicalCode.ERROR;
  }

  private GrpcHelper() {}
}
