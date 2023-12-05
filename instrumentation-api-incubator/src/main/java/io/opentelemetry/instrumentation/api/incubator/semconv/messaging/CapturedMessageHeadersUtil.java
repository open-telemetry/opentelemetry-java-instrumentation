/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static java.util.Collections.unmodifiableList;

import io.opentelemetry.api.common.AttributeKey;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

final class CapturedMessageHeadersUtil {

  private static final ConcurrentMap<String, AttributeKey<List<String>>> attributeKeysCache =
      new ConcurrentHashMap<>();

  static List<String> lowercase(List<String> names) {
    return unmodifiableList(
        names.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toList()));
  }

  static AttributeKey<List<String>> attributeKey(String headerName) {
    return attributeKeysCache.computeIfAbsent(headerName, n -> createKey(n));
  }

  private static AttributeKey<List<String>> createKey(String headerName) {
    // headerName is always lowercase, see MessagingAttributesExtractor
    String key = "messaging.header." + headerName.replace('-', '_');
    return AttributeKey.stringArrayKey(key);
  }

  private CapturedMessageHeadersUtil() {}
}
