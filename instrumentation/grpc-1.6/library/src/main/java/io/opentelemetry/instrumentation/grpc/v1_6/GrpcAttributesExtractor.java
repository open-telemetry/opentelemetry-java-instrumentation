/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Metadata;
import io.grpc.Status;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;

final class GrpcAttributesExtractor implements AttributesExtractor<GrpcRequest, Status> {

  // copied from RpcIncubatingAttributes
  private static final AttributeKey<String> RPC_STATUS_CODE =
      AttributeKey.stringKey("rpc.status_code");
  private final GrpcRpcAttributesGetter getter;
  private final Map<String, AttributeKey<List<String>>> requestHeaderAttributeKeys =
      new HashMap<>();

  GrpcAttributesExtractor(
      GrpcRpcAttributesGetter getter, Collection<String> requestCapturedHeaders) {
    this.getter = getter;
    for (String name : requestCapturedHeaders) {
      String key = name.toLowerCase(Locale.ROOT);
      if (isAsciiMetadataKey(key)) {
        requestHeaderAttributeKeys.put(
            key, AttributeKey.stringArrayKey("rpc.request.header." + key));
      }
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
      attributes.put(RPC_STATUS_CODE, status.getCode().name());
    }
    if (request.getMetadata() == null) {
      return;
    }
    for (Map.Entry<String, AttributeKey<List<String>>> entry :
        requestHeaderAttributeKeys.entrySet()) {
      List<String> value = getter.metadataValue(request, entry.getKey());
      if (!value.isEmpty()) {
        attributes.put(entry.getValue(), value);
      }
    }
  }

  // Metadata.Key.of() rejects binary and pseudo-headers as well as invalid ASCII metadata names.
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
