/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.cache;

import io.opentelemetry.instrumentation.api.cache.internal.weaklockfree.WeakConcurrentMap;
import java.util.function.Function;

final class WeakLockFreeCache<K, V> implements Cache<K, V> {

  private final WeakConcurrentMap<K, V> delegate;

  WeakLockFreeCache() {
    this.delegate = new WeakConcurrentMap.WithInlinedExpunction<>();
  }

  @Override
  public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    return delegate.computeIfAbsent(key, mappingFunction);
  }

  @Override
  public V get(K key) {
    return delegate.getIfPresent(key);
  }

  @Override
  public void put(K key, V value) {
    delegate.put(key, value);
  }

  @Override
  public void remove(K key) {
    delegate.remove(key);
  }

  // Visible for testing
  int size() {
    return delegate.approximateSize();
  }
}
