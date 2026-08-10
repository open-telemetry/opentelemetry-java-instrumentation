/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.util.List;
import java.util.Locale;

final class CapturedGrpcMetadataUtil {
  private static final String RPC_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX =
      "rpc.grpc.request.metadata.";
  private static final String RPC_STABLE_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX =
      "rpc.request.metadata.";

  // bounded because metadata keys can be selected by wildcard, and are then peer-controlled
  private static final Cache<String, AttributeKey<List<String>>> requestKeysCache =
      Cache.bounded(64);
  private static final Cache<String, AttributeKey<List<String>>> stableRequestKeysCache =
      Cache.bounded(64);

  static List<String> lowercase(List<String> names) {
    return unmodifiableList(names.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(toList()));
  }

  static IncludeExclude lowercase(IncludeExclude selector) {
    return IncludeExclude.builder()
        .setIncluded(lowercase(selector.getIncluded()))
        .setExcluded(lowercase(selector.getExcluded()))
        .build();
  }

  static AttributeKey<List<String>> requestAttributeKey(String metadataKey) {
    return requestKeysCache.computeIfAbsent(
        metadataKey,
        key -> AttributeKey.stringArrayKey(RPC_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX + key));
  }

  static AttributeKey<List<String>> stableRequestAttributeKey(String metadataKey) {
    return stableRequestKeysCache.computeIfAbsent(
        metadataKey,
        key -> AttributeKey.stringArrayKey(RPC_STABLE_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX + key));
  }

  private CapturedGrpcMetadataUtil() {}
}
