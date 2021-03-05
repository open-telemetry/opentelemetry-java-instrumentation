/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_5;

import io.grpc.Status.Code;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

final class GrpcHelper {

  public static final AttributeKey<String> MESSAGE_TYPE = AttributeKey.stringKey("message.type");
  public static final AttributeKey<Long> MESSAGE_ID = AttributeKey.longKey("message.id");

  public static void prepareSpan(Span span, String fullMethodName) {

    int slash = fullMethodName.indexOf('/');
    String serviceName = slash == -1 ? fullMethodName : fullMethodName.substring(0, slash);
    String methodName = slash == -1 ? null : fullMethodName.substring(slash + 1);

    span.setAttribute(SemanticAttributes.RPC_SERVICE, serviceName);
    if (methodName != null) {
      span.setAttribute(SemanticAttributes.RPC_METHOD, methodName);
    }
  }

  public static StatusCode statusFromGrpcStatus(io.grpc.Status grpcStatus) {
    return codeFromGrpcCode(grpcStatus.getCode());
  }

  private static StatusCode codeFromGrpcCode(Code grpcCode) {
    return grpcCode.equals(Code.OK) ? StatusCode.UNSET : StatusCode.ERROR;
  }

  private GrpcHelper() {}
}
