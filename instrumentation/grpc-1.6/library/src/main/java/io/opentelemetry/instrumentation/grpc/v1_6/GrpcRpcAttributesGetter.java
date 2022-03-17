/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcAttributesGetter;
import javax.annotation.Nullable;

enum GrpcRpcAttributesGetter implements RpcAttributesGetter<GrpcRequest> {
  INSTANCE;

  @Override
  public String system(GrpcRequest request) {
    return "grpc";
  }

  @Override
  @Nullable
  public String service(GrpcRequest request) {
    String fullMethodName = request.getMethod().getFullMethodName();
    int slashIndex = fullMethodName.lastIndexOf('/');
    if (slashIndex == -1) {
      return null;
    }
    return fullMethodName.substring(0, slashIndex);
  }

  @Override
  @Nullable
  public String method(GrpcRequest request) {
    String fullMethodName = request.getMethod().getFullMethodName();
    int slashIndex = fullMethodName.lastIndexOf('/');
    if (slashIndex == -1) {
      return null;
    }
    return fullMethodName.substring(slashIndex + 1);
  }
}
