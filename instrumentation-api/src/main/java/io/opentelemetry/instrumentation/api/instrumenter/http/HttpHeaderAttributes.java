/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.caching.Cache;
import java.util.List;

final class HttpHeaderAttributes {

  private static final Cache<String, AttributeKey<List<String>>> requestKeysCache =
      Cache.newBuilder().setMaximumSize(32).build();
  private static final Cache<String, AttributeKey<List<String>>> responseKeysCache =
      Cache.newBuilder().setMaximumSize(32).build();

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
