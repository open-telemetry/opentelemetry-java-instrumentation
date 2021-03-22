/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.caching;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

final class WeakLockFreeCache<K, V> implements Cache<K, V> {

  private static final AtomicLong ID = new AtomicLong();

  private final WeakConcurrentMap<K, V> delegate;

  WeakLockFreeCache() {
    // Don't automatically create cleaner thread to oveerride classloader.
    this.delegate = new WeakConcurrentMap<>(false, true);

    Thread thread = new Thread(delegate);
    // This class is in the bootstrap classloader and cleanup never requires user code so force the
    // context classloader to bootstrap.
    thread.setContextClassLoader(ClassLoader.getSystemClassLoader());
    thread.setName("weak-ref-cleaner-" + ID.getAndIncrement());
    thread.setPriority(Thread.MIN_PRIORITY);
    thread.setDaemon(true);
    thread.start();
  }

  @Override
  public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    V value = get(key);
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
