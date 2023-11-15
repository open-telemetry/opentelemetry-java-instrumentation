/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static java.util.Collections.unmodifiableList;

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
  private static final ConcurrentMap<String, AttributeKey<List<String>>>
      oldSemconvRequestKeysCache = new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, AttributeKey<List<String>>>
      stableSemconvRequestKeysCache = new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, AttributeKey<List<String>>>
      oldSemconvResponseKeysCache = new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, AttributeKey<List<String>>>
      stableSemconvResponseKeysCache = new ConcurrentHashMap<>();

  static List<String> lowercase(List<String> names) {
    return unmodifiableList(
        names.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toList()));
  }

  static AttributeKey<List<String>> oldSemconvRequestAttributeKey(String headerName) {
    return oldSemconvRequestKeysCache.computeIfAbsent(
        headerName, n -> createOldSemconvKey("request", n));
  }

  static AttributeKey<List<String>> stableSemconvRequestAttributeKey(String headerName) {
    return stableSemconvRequestKeysCache.computeIfAbsent(
        headerName, n -> createStableSemconvKey("request", n));
  }

  static AttributeKey<List<String>> oldSemconvResponseAttributeKey(String headerName) {
    return oldSemconvResponseKeysCache.computeIfAbsent(
        headerName, n -> createOldSemconvKey("response", n));
  }

  static AttributeKey<List<String>> stableSemconvResponseAttributeKey(String headerName) {
    return stableSemconvResponseKeysCache.computeIfAbsent(
        headerName, n -> createStableSemconvKey("response", n));
  }

  private static AttributeKey<List<String>> createOldSemconvKey(String type, String headerName) {
    // headerName is always lowercase, see CapturedHttpHeadersUtil#lowercase
    String key = "http." + type + ".header." + headerName.replace('-', '_');
    return AttributeKey.stringArrayKey(key);
  }

  private static AttributeKey<List<String>> createStableSemconvKey(String type, String headerName) {
    // headerName is always lowercase, see CapturedHttpHeadersUtil#lowercase
    String key = "http." + type + ".header." + headerName;
    return AttributeKey.stringArrayKey(key);
  }

  private CapturedHttpHeadersUtil() {}
}
