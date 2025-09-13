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

  private static final ConcurrentMap<String, AttributeKey<List<String>>>
      oldSemconvAttributeKeysCache = new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, AttributeKey<List<String>>>
      stableSemconvAttributeKeysCache = new ConcurrentHashMap<>();

  static AttributeKey<List<String>> oldSemconvAttributeKey(String headerName) {
    return oldSemconvAttributeKeysCache.computeIfAbsent(headerName, n -> createOldSemconvKey(n));
  }

  static AttributeKey<List<String>> stableSemconvAttributeKey(String headerName) {
    return stableSemconvAttributeKeysCache.computeIfAbsent(
        headerName, n -> createStableSemconvKey(n));
  }

  private static AttributeKey<List<String>> createOldSemconvKey(String headerName) {
    String key = "messaging.header." + headerName.replace('-', '_');
    return AttributeKey.stringArrayKey(key);
  }

  private static AttributeKey<List<String>> createStableSemconvKey(String headerName) {
    String key = "messaging.header." + headerName;
    return AttributeKey.stringArrayKey(key);
  }

  private CapturedMessageHeadersUtil() {}
}
