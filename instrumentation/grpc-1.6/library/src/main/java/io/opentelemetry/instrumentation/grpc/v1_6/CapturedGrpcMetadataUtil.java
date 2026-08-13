/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.stream.Collectors.toList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CapturedGrpcMetadataUtil {
  private static final String RPC_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX =
      "rpc.grpc.request.metadata.";
  private static final String RPC_STABLE_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX =
      "rpc.request.metadata.";

  static List<String> lowercase(List<String> names) {
    return unmodifiableList(names.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(toList()));
  }

  static IncludeExclude lowercase(IncludeExclude selector) {
    return IncludeExclude.builder()
        .setIncluded(lowercase(selector.getIncluded()))
        .setExcluded(lowercase(selector.getExcluded()))
        .build();
  }

  static Map<String, AttributeKey<List<String>>> createLiteralRequestAttributeKeys(
      IncludeExclude selector) {
    return createLiteralAttributeKeys(selector, RPC_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX);
  }

  static Map<String, AttributeKey<List<String>>> createLiteralStableRequestAttributeKeys(
      IncludeExclude selector) {
    return createLiteralAttributeKeys(selector, RPC_STABLE_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX);
  }

  static AttributeKey<List<String>> requestAttributeKey(
      String metadataKey, Map<String, AttributeKey<List<String>>> literalAttributeKeys) {
    return attributeKey(
        metadataKey, literalAttributeKeys, RPC_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX);
  }

  static AttributeKey<List<String>> stableRequestAttributeKey(
      String metadataKey, Map<String, AttributeKey<List<String>>> literalAttributeKeys) {
    return attributeKey(
        metadataKey, literalAttributeKeys, RPC_STABLE_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX);
  }

  private static Map<String, AttributeKey<List<String>>> createLiteralAttributeKeys(
      IncludeExclude selector, String prefix) {
    Map<String, AttributeKey<List<String>>> result = new HashMap<>();
    for (String pattern : selector.getIncluded()) {
      if (pattern.indexOf('*') == -1 && pattern.indexOf('?') == -1) {
        result.put(pattern, AttributeKey.stringArrayKey(prefix + pattern));
      }
    }
    return unmodifiableMap(result);
  }

  private static AttributeKey<List<String>> attributeKey(
      String metadataKey,
      Map<String, AttributeKey<List<String>>> literalAttributeKeys,
      String prefix) {
    AttributeKey<List<String>> attributeKey = literalAttributeKeys.get(metadataKey);
    return attributeKey != null ? attributeKey : AttributeKey.stringArrayKey(prefix + metadataKey);
  }

  private CapturedGrpcMetadataUtil() {}
}
