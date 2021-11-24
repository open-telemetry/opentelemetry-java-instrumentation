/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.api.common.AttributeKey;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class HttpHeaderAttributes {

  // these are naturally bounded because they only store keys listed in
  // otel.instrumentation.http.capture-headers.server.request and
  // otel.instrumentation.http.capture-headers.server.response
  private static final ConcurrentMap<String, AttributeKey<List<String>>> requestKeysCache =
      new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, AttributeKey<List<String>>> responseKeysCache =
      new ConcurrentHashMap<>();

  static AttributeKey<List<String>> requestAttributeKey(String headerName) {
    return requestKeysCache.computeIfAbsent(headerName, n -> createKey("request", n));
  }

  static AttributeKey<List<String>> responseAttributeKey(String headerName) {
    return responseKeysCache.computeIfAbsent(headerName, n -> createKey("response", n));
  }

  private static AttributeKey<List<String>> createKey(String type, String headerName) {
    // headerName is always lowercase, see CapturedHttpHeaders
    String key = "http." + type + ".header." + headerName.replace('-', '_');
    return AttributeKey.stringArrayKey(key);
  }

  private HttpHeaderAttributes() {}
}
