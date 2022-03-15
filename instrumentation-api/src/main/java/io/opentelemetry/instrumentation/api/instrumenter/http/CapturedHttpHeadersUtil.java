/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.config.Config;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

final class CapturedHttpHeadersUtil {

  private static final String CLIENT_REQUEST_PROPERTY =
      "otel.instrumentation.http.capture-headers.client.request";
  private static final String CLIENT_RESPONSE_PROPERTY =
      "otel.instrumentation.http.capture-headers.client.response";
  private static final String SERVER_REQUEST_PROPERTY =
      "otel.instrumentation.http.capture-headers.server.request";
  private static final String SERVER_RESPONSE_PROPERTY =
      "otel.instrumentation.http.capture-headers.server.response";

  static final List<String> clientRequestHeaders;
  static final List<String> clientResponseHeaders;
  static final List<String> serverRequestHeaders;
  static final List<String> serverResponseHeaders;

  static {
    Config config = Config.get();
    clientRequestHeaders = config.getList(CLIENT_REQUEST_PROPERTY, emptyList());
    clientResponseHeaders = config.getList(CLIENT_RESPONSE_PROPERTY, emptyList());
    serverRequestHeaders = config.getList(SERVER_REQUEST_PROPERTY, emptyList());
    serverResponseHeaders = config.getList(SERVER_RESPONSE_PROPERTY, emptyList());
  }

  // these are naturally bounded because they only store keys listed in
  // otel.instrumentation.http.capture-headers.server.request and
  // otel.instrumentation.http.capture-headers.server.response
  private static final ConcurrentMap<String, AttributeKey<List<String>>> requestKeysCache =
      new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, AttributeKey<List<String>>> responseKeysCache =
      new ConcurrentHashMap<>();

  static List<String> lowercase(List<String> names) {
    return unmodifiableList(
        names.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toList()));
  }

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

  private CapturedHttpHeadersUtil() {}
}
