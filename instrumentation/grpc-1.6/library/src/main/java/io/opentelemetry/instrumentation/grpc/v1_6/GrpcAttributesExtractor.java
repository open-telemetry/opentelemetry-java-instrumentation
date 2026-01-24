/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static io.opentelemetry.instrumentation.grpc.v1_6.CapturedGrpcMetadataUtil.lowercase;
import static io.opentelemetry.instrumentation.grpc.v1_6.CapturedGrpcMetadataUtil.requestAttributeKey;
import static io.opentelemetry.instrumentation.grpc.v1_6.CapturedGrpcMetadataUtil.stableRequestAttributeKey;

import io.grpc.Status;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.List;
import javax.annotation.Nullable;

final class GrpcAttributesExtractor implements AttributesExtractor<GrpcRequest, Status> {

  // Stable semconv key
  private static final AttributeKey<String> RPC_RESPONSE_STATUS_CODE =
      AttributeKey.stringKey("rpc.response.status_code");

  // copied from RpcIncubatingAttributes
  @Deprecated // use RPC_RESPONSE_STATUS_CODE for stable semconv
  private static final AttributeKey<Long> RPC_GRPC_STATUS_CODE =
      AttributeKey.longKey("rpc.grpc.status_code");

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

  @SuppressWarnings("deprecation") // until old rpc semconv are dropped
  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      GrpcRequest request,
      @Nullable Status status,
      @Nullable Throwable error) {
    if (status != null) {
      long statusCodeValue = status.getCode().value();
      if (SemconvStability.emitStableRpcSemconv()) {
        attributes.put(RPC_RESPONSE_STATUS_CODE, String.valueOf(statusCodeValue));
      }
      if (SemconvStability.emitOldRpcSemconv()) {
        attributes.put(RPC_GRPC_STATUS_CODE, statusCodeValue);
      }
    }
    for (String key : capturedRequestMetadata) {
      List<String> value = getter.metadataValue(request, key);
      if (!value.isEmpty()) {
        if (SemconvStability.emitStableRpcSemconv()) {
          attributes.put(stableRequestAttributeKey(key), value);
        }
        if (SemconvStability.emitOldRpcSemconv()) {
          attributes.put(requestAttributeKey(key), value);
        }
      }
    }
  }
}
