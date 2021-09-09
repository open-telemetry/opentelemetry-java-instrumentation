/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Status;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

final class GrpcAttributesExtractor extends AttributesExtractor<GrpcRequest, Status> {
  @Override
  protected void onStart(AttributesBuilder attributes, GrpcRequest grpcRequest) {
    // No request attributes
  }

  @Override
  protected void onEnd(
      AttributesBuilder attributes,
      GrpcRequest request,
      @Nullable Status status,
      @Nullable Throwable error) {
    if (status != null) {
      attributes.put(SemanticAttributes.RPC_GRPC_STATUS_CODE, status.getCode().value());
    }
  }
}
