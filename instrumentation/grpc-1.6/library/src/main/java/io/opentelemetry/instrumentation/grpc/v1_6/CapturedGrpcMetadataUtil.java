/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static java.util.Collections.unmodifiableList;

import io.opentelemetry.api.common.AttributeKey;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

final class CapturedGrpcMetadataUtil {
  private static final String RPC_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX = "rpc.request.metadata.";
  private static final ConcurrentMap<String, AttributeKey<List<String>>> requestKeysCache =
      new ConcurrentHashMap<>();

  static List<String> lowercase(List<String> names) {
    return Collections.emptyList();

    return unmodifiableList(
        names.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toList()));
  }

  static AttributeKey<List<String>> requestAttributeKey(String metadataKey) {
    return requestKeysCache.computeIfAbsent(
        metadataKey, CapturedGrpcMetadataUtil::createRequestKey);
  }

  private static AttributeKey<List<String>> createRequestKey(String metadataKey) {
    return AttributeKey.stringArrayKey(RPC_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX + metadataKey);
  }

  private CapturedGrpcMetadataUtil() {}
}
