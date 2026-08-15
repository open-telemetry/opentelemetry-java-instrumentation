/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static java.util.Collections.unmodifiableMap;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.internal.CapturedNames;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CapturedGrpcMetadataUtil {
  private static final String RPC_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX =
      "rpc.grpc.request.metadata.";
  private static final String RPC_STABLE_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX =
      "rpc.request.metadata.";

  static Map<String, AttributeKey<List<String>>> createExactRequestAttributeKeys(
      CapturedNames requestMetadata) {
    return createExactAttributeKeys(requestMetadata, RPC_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX);
  }

  static Map<String, AttributeKey<List<String>>> createExactStableRequestAttributeKeys(
      CapturedNames requestMetadata) {
    return createExactAttributeKeys(
        requestMetadata, RPC_STABLE_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX);
  }

  static AttributeKey<List<String>> requestAttributeKey(
      String metadataKey, Map<String, AttributeKey<List<String>>> exactAttributeKeys) {
    return attributeKey(metadataKey, exactAttributeKeys, RPC_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX);
  }

  static AttributeKey<List<String>> stableRequestAttributeKey(
      String metadataKey, Map<String, AttributeKey<List<String>>> exactAttributeKeys) {
    return attributeKey(
        metadataKey, exactAttributeKeys, RPC_STABLE_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX);
  }

  private static Map<String, AttributeKey<List<String>>> createExactAttributeKeys(
      CapturedNames requestMetadata, String prefix) {
    Map<String, AttributeKey<List<String>>> result = new HashMap<>();
    for (String metadataKey : requestMetadata.exactNames()) {
      result.put(metadataKey, AttributeKey.stringArrayKey(prefix + metadataKey));
    }
    return unmodifiableMap(result);
  }

  private static AttributeKey<List<String>> attributeKey(
      String metadataKey,
      Map<String, AttributeKey<List<String>>> exactAttributeKeys,
      String prefix) {
    AttributeKey<List<String>> attributeKey = exactAttributeKeys.get(metadataKey);
    return attributeKey != null ? attributeKey : AttributeKey.stringArrayKey(prefix + metadataKey);
  }

  private CapturedGrpcMetadataUtil() {}
}
