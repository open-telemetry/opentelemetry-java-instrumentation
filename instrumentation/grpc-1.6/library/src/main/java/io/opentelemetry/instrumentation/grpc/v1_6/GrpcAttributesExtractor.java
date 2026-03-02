/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableRpcSemconv;
import static io.opentelemetry.instrumentation.grpc.v1_6.CapturedGrpcMetadataUtil.lowercase;
import static io.opentelemetry.instrumentation.grpc.v1_6.CapturedGrpcMetadataUtil.requestAttributeKey;
import static io.opentelemetry.instrumentation.grpc.v1_6.CapturedGrpcMetadataUtil.stableRequestAttributeKey;

import io.grpc.Status;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.List;
import javax.annotation.Nullable;

final class GrpcAttributesExtractor implements AttributesExtractor<GrpcRequest, Status> {

  // copied from RpcIncubatingAttributes
  private static final AttributeKey<Long> RPC_GRPC_STATUS_CODE =
      AttributeKey.longKey("rpc.grpc.status_code");
  private static final AttributeKey<String> RPC_RESPONSE_STATUS_CODE =
      AttributeKey.stringKey("rpc.response.status_code");

  private final GrpcRpcAttributesGetter getter;
  private final List<String> capturedRequestMetadata;

  GrpcAttributesExtractor(
      GrpcRpcAttributesGetter getter, List<String> requestMetadataValuesToCapture) {
    this.getter = getter;
    this.capturedRequestMetadata = lowercase(requestMetadataValuesToCapture);
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, GrpcRequest request) {
    // Request attributes captured on request end.
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      GrpcRequest request,
      @Nullable Status status,
      @Nullable Throwable error) {
    if (status != null) {
      if (emitOldRpcSemconv()) {
        attributes.put(RPC_GRPC_STATUS_CODE, status.getCode().value());
      }
      if (emitStableRpcSemconv()) {
        attributes.put(RPC_RESPONSE_STATUS_CODE, status.getCode().name());
      }
    }
    for (String key : capturedRequestMetadata) {
      List<String> value = getter.metadataValue(request, key);
      if (!value.isEmpty()) {
        if (emitOldRpcSemconv()) {
          attributes.put(requestAttributeKey(key), value);
        }
        if (emitStableRpcSemconv()) {
          attributes.put(stableRequestAttributeKey(key), value);
        }
      }
    }
  }
}
