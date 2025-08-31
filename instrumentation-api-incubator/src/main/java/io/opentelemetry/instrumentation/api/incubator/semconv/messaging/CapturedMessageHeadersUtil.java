/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import io.opentelemetry.api.common.AttributeKey;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class CapturedMessageHeadersUtil {

  private static final ConcurrentMap<String, AttributeKey<List<String>>> attributeKeysCache =
      new ConcurrentHashMap<>();

  static AttributeKey<List<String>> attributeKey(String headerName) {
    return attributeKeysCache.computeIfAbsent(headerName, n -> createKey(n));
  }

  private static AttributeKey<List<String>> createKey(String headerName) {
    String key = "messaging.header." + headerName;
    return AttributeKey.stringArrayKey(key);
  }

  private CapturedMessageHeadersUtil() {}
}
