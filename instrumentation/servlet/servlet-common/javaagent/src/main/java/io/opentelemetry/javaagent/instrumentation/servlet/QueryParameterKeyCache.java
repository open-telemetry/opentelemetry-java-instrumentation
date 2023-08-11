/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import io.opentelemetry.api.common.AttributeKey;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class QueryParameterKeyCache {

  public static final String KEY_PREFIX = "servlet.request.parameter.";
  private static final ConcurrentMap<String, AttributeKey<List<String>>> cache =
      new ConcurrentHashMap<>();

  private QueryParameterKeyCache() {}

  static AttributeKey<List<String>> get(String headerName) {
    // TODO: Limit cache size to prevent cache bloating attacks
    return cache.computeIfAbsent(headerName, QueryParameterKeyCache::createKey);
  }

  private static AttributeKey<List<String>> createKey(String parameterName) {
    // normalize parameter name similarly as is done with header names when header values are
    // captured as span attributes
    parameterName = parameterName.toLowerCase(Locale.ROOT);
    String key = KEY_PREFIX + parameterName.replace('-', '_');
    return AttributeKey.stringArrayKey(key);
  }
}
