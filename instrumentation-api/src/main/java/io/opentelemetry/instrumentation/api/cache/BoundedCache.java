/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.cache;

import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import javax.annotation.Nullable;

final class BoundedCache<K, V> implements Cache<K, V> {

  private final ConcurrentMap<K, V> delegate;

  BoundedCache(ConcurrentMap<K, V> delegate) {
    this.delegate = delegate;
  }

  @Override
  public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    return delegate.computeIfAbsent(key, mappingFunction);
  }

  @Nullable
  @Override
  public V get(K key) {
    return delegate.get(key);
  }

  @Override
  public void put(K key, V value) {
    delegate.put(key, value);
  }

  @Override
  public void remove(K key) {
    delegate.remove(key);
  }
}
