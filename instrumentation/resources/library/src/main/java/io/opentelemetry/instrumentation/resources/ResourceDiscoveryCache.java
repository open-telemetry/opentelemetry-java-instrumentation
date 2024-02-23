/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class ResourceDiscoveryCache {
  private static final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();

  private ResourceDiscoveryCache() {}

  // visible for testing
  public static void resetForTest() {
    cache.clear();
  }

  @SuppressWarnings("unchecked")
  public static <T> T get(String key, Supplier<T> supplier) {
    return (T) cache.computeIfAbsent(key, k -> supplier.get());
  }
}
