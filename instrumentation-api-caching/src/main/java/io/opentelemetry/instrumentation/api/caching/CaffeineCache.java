/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.caching;

import java.util.Set;
import java.util.function.Function;

final class CaffeineCache<K, V> implements Cache<K, V> {

  private final com.github.benmanes.caffeine.cache.Cache<K, V> delegate;

  CaffeineCache(com.github.benmanes.caffeine.cache.Cache<K, V> delegate) {
    this.delegate = delegate;
  }

  @Override
  public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    return delegate.get(key, mappingFunction);
  }

  // Visible for testing
  Set<K> keySet() {
    return delegate.asMap().keySet();
  }

  // Visible for testing
  void cleanup() {
    delegate.cleanUp();
  }
}
