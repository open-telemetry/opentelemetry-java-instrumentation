/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import io.opentelemetry.api.common.AttributeKey;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

final class CapturedHttpHeadersUtil {

  // these are naturally bounded because they only store keys listed in
  // otel.instrumentation.http.server.capture-request-headers and
  // otel.instrumentation.http.server.capture-response-headers
  private static final ConcurrentMap<String, AttributeKey<List<String>>> requestKeysCache =
      new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, AttributeKey<List<String>>> responseKeysCache =
      new ConcurrentHashMap<>();

  static String[] lowercase(List<String> names) {
    return names.stream()
        .map(s -> s.toLowerCase(Locale.ROOT))
        .collect(Collectors.toList())
        .toArray(new String[0]);
  }

  static AttributeKey<List<String>> requestAttributeKey(String headerName) {
    return requestKeysCache.computeIfAbsent(headerName, n -> createKey("request", n));
  }

  static AttributeKey<List<String>> responseAttributeKey(String headerName) {
    return responseKeysCache.computeIfAbsent(headerName, n -> createKey("response", n));
  }

  private static AttributeKey<List<String>> createKey(String type, String headerName) {
    // headerName is always lowercase, see CapturedHttpHeadersUtil#lowercase
    String key = "http." + type + ".header." + headerName;
    return AttributeKey.stringArrayKey(key);
  }

  private CapturedHttpHeadersUtil() {}
}
