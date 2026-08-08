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

import io.grpc.Metadata;
import io.grpc.Status;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
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
  @Nullable private final IncludeExclude requestMetadata;

  GrpcAttributesExtractor(
      GrpcRpcAttributesGetter getter, @Nullable IncludeExclude requestMetadata) {
    this.getter = getter;
    this.requestMetadata = requestMetadata == null ? null : lowercase(requestMetadata);
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
    if (requestMetadata == null || request.getMetadata() == null) {
      return;
    }
    for (String key : request.getMetadata().keys()) {
      if (key.startsWith(":")
          || key.endsWith(Metadata.BINARY_HEADER_SUFFIX)
          || !requestMetadata.matches(key)) {
        continue;
      }
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
