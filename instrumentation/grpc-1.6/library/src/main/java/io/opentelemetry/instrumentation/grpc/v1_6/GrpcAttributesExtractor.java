/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Status;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

import java.util.List;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

final class GrpcAttributesExtractor implements AttributesExtractor<GrpcRequest, Status> {
  // TODO: This should be added to package io.opentelemetry.semconv.trace.attributes in the next release
  public static final String GRPC_METADATA_ATTRIBUTE_VALUE_PREFIX = "rpc.request.metadata";

  private final GrpcRpcAttributesGetter getter;
  private final List<String> requestMetadataValuesToCapture;

  GrpcAttributesExtractor(
      GrpcRpcAttributesGetter getter,
      List<String> requestMetadataValuesToCapture) {
    this.getter = getter;
    this.requestMetadataValuesToCapture = requestMetadataValuesToCapture;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, GrpcRequest grpcRequest) {
    // Request attributes captured on request end (onEnd)
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

    if (requestMetadataValuesToCapture != null) {
      for (String key : requestMetadataValuesToCapture) {
        internalSet(
            attributes,
            AttributeKey.stringArrayKey(GRPC_METADATA_ATTRIBUTE_VALUE_PREFIX + "." + key),
            getter.metadataValue(request, key)
        );
      }
    }
  }
}
