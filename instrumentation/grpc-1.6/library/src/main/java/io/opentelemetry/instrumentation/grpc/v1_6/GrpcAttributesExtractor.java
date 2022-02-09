/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Status;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class GrpcAttributesExtractor implements AttributesExtractor<GrpcRequest, Status> {
  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, GrpcRequest grpcRequest) {
    // No request attributes
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      GrpcRequest request,
      @Nullable Status status,
      @Nullable Throwable error) {
    if (status != null) {
      attributes.put(SemanticAttributes.RPC_GRPC_STATUS_CODE, status.getCode().value());
    }
  }
}
