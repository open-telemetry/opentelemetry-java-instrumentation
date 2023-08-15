/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.util.List;
import java.util.Locale;

class QueryParameterKeyCache {

  public static final String KEY_PREFIX = "servlet.request.parameter.";
  private static final Cache<String, AttributeKey<List<String>>> cache = Cache.bounded(500);

  private QueryParameterKeyCache() {}

  static AttributeKey<List<String>> get(String parameterName) {
    return cache.computeIfAbsent(parameterName, QueryParameterKeyCache::createKey);
  }

  private static AttributeKey<List<String>> createKey(String parameterName) {
    // normalize parameter name similarly as is done with header names when header values are
    // captured as span attributes
    parameterName = parameterName.toLowerCase(Locale.ROOT);
    String key = KEY_PREFIX + parameterName.replace('-', '_');
    return AttributeKey.stringArrayKey(key);
  }
}
