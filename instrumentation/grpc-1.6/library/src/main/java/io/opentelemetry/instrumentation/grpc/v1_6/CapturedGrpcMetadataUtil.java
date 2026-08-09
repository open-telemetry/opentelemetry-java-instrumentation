/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.util.List;
import java.util.Locale;

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

  static AttributeKey<List<String>> requestAttributeKey(String metadataKey) {
    return AttributeKey.stringArrayKey(RPC_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX + metadataKey);
  }

  static AttributeKey<List<String>> stableRequestAttributeKey(String metadataKey) {
    return AttributeKey.stringArrayKey(
        RPC_STABLE_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX + metadataKey);
  }

  private CapturedGrpcMetadataUtil() {}
}
