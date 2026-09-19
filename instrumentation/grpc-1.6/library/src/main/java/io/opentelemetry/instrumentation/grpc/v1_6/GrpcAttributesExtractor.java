/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableRpcSemconv;
import static io.opentelemetry.instrumentation.grpc.v1_6.CapturedGrpcMetadataUtil.createLiteralRequestAttributeKeys;
import static io.opentelemetry.instrumentation.grpc.v1_6.CapturedGrpcMetadataUtil.createLiteralStableRequestAttributeKeys;
import static io.opentelemetry.instrumentation.grpc.v1_6.CapturedGrpcMetadataUtil.lowercase;
import static io.opentelemetry.instrumentation.grpc.v1_6.CapturedGrpcMetadataUtil.requestAttributeKey;
import static io.opentelemetry.instrumentation.grpc.v1_6.CapturedGrpcMetadataUtil.stableRequestAttributeKey;
import static java.util.Collections.emptyMap;

import io.grpc.Metadata;
import io.grpc.Status;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;

final class GrpcAttributesExtractor implements AttributesExtractor<GrpcRequest, Status> {

  // copied from RpcIncubatingAttributes
  private static final AttributeKey<Long> RPC_GRPC_STATUS_CODE =
      AttributeKey.longKey("rpc.grpc.status_code");
  private static final AttributeKey<String> RPC_RESPONSE_STATUS_CODE =
      AttributeKey.stringKey("rpc.response.status_code");
  private final GrpcRpcAttributesGetter getter;
  @Nullable private final IncludeExclude requestMetadata;

  // Only literal included names are stored here. We intentionally do not cache wildcard or
  // exclude-only matches, even in a bounded cache: peer-controlled names could churn its entries,
  // making allocation behavior depend on prior traffic. Creating those attribute keys on demand
  // provides a fixed per-capture cost with no peer-controlled retained state, while literal
  // configured keys remain allocation-free.
  private final Map<String, AttributeKey<List<String>>> literalRequestAttributeKeys;
  private final Map<String, AttributeKey<List<String>>> literalStableRequestAttributeKeys;

  GrpcAttributesExtractor(
      GrpcRpcAttributesGetter getter, @Nullable IncludeExclude requestMetadata) {
    this.getter = getter;
    if (requestMetadata == null || requestMetadata.isEmpty()) {
      this.requestMetadata = null;
      literalRequestAttributeKeys = emptyMap();
      literalStableRequestAttributeKeys = emptyMap();
    } else {
      this.requestMetadata = lowercase(requestMetadata);
      literalRequestAttributeKeys = createLiteralRequestAttributeKeys(this.requestMetadata);
      literalStableRequestAttributeKeys =
          createLiteralStableRequestAttributeKeys(this.requestMetadata);
    }
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
      // binary metadata and HTTP/2 pseudo-headers are never captured, even when the selector
      // matches, because reading them below with Metadata.Key.of() would throw
      if (!isAsciiMetadataKey(key)) {
        continue;
      }
      // gRPC lowercases metadata key names, but normalize defensively so that matching is
      // case-insensitive as documented and an excluded key cannot be captured because a transport
      // surfaced its name with different casing
      String metadataKey = key.toLowerCase(Locale.ROOT);
      if (!requestMetadata.matches(metadataKey)) {
        continue;
      }
      List<String> value = getter.metadataValue(request, metadataKey);
      if (!value.isEmpty()) {
        if (emitOldRpcSemconv()) {
          attributes.put(requestAttributeKey(metadataKey, literalRequestAttributeKeys), value);
        }
        if (emitStableRpcSemconv()) {
          attributes.put(
              stableRequestAttributeKey(metadataKey, literalStableRequestAttributeKeys), value);
        }
      }
    }
  }

  // Returns whether the key names ASCII metadata that Metadata.Key.of() accepts. Metadata received
  // from a peer can contain HTTP/2 pseudo-headers and header names built from HTTP token characters
  // that gRPC does not allow in a metadata key.
  private static boolean isAsciiMetadataKey(String key) {
    if (key.isEmpty() || key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
      return false;
    }
    for (int i = 0; i < key.length(); i++) {
      char c = key.charAt(i);
      // the character set accepted by Metadata.Key, which lowercases the name before validating it
      boolean valid =
          (c >= 'a' && c <= 'z')
              || (c >= 'A' && c <= 'Z')
              || (c >= '0' && c <= '9')
              || c == '-'
              || c == '_'
              || c == '.';
      if (!valid) {
        return false;
      }
    }
    return true;
  }
}
