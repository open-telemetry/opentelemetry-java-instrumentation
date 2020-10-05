/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.util.concurrent.Callable;

public interface WeakCache<K, V> {
  interface Provider<K, V> {
    WeakCache<K, V> newWeakCache();

    WeakCache<K, V> newWeakCache(long maxSize);
  }

  V getIfPresent(K key);

  V getIfPresentOrCompute(K key, Callable<? extends V> loader);

  V get(K key, Callable<? extends V> loader);

  void put(K key, V value);
}
