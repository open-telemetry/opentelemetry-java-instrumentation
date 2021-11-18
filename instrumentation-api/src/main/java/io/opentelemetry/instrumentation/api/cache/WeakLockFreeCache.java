/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.cache;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import java.util.function.Function;

final class WeakLockFreeCache<K, V> implements Cache<K, V> {

  private final WeakConcurrentMap<K, V> delegate;

  WeakLockFreeCache() {
    this.delegate = new WeakConcurrentMap.WithInlinedExpunction<>();
  }

  @Override
  public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    V value = get(key);
    if (value != null) {
      return value;
    }
    // Best we can do, we don't expect high contention with this implementation. Note, this
    // prevents executing mappingFunction twice but it does not prevent executing mappingFunction
    // if there is a concurrent put operation as would be the case for ConcurrentHashMap. However,
    // we would never expect an order guarantee in this case anyways so it still has the same
    // safety.
    synchronized (delegate) {
      value = get(key);
      if (value != null) {
        return value;
      }
      value = mappingFunction.apply(key);
      V previous = delegate.putIfAbsent(key, value);
      if (previous != null) {
        return previous;
      }
      return value;
    }
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
