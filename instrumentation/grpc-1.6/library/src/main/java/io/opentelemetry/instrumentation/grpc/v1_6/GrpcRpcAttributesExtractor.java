/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Status;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcAttributesExtractor;
import javax.annotation.Nullable;

final class GrpcRpcAttributesExtractor extends RpcAttributesExtractor<GrpcRequest, Status> {
  @Override
  protected String system(GrpcRequest request) {
    return "grpc";
  }

  @Override
  @Nullable
  protected String service(GrpcRequest request) {
    String fullMethodName = request.getMethod().getFullMethodName();
    int slashIndex = fullMethodName.lastIndexOf('/');
    if (slashIndex == -1) {
      return null;
    }
    return fullMethodName.substring(0, slashIndex);
  }

  @Override
  @Nullable
  protected String method(GrpcRequest request) {
    String fullMethodName = request.getMethod().getFullMethodName();
    int slashIndex = fullMethodName.lastIndexOf('/');
    if (slashIndex == -1) {
      return null;
    }
    return fullMethodName.substring(slashIndex + 1);
  }
}
