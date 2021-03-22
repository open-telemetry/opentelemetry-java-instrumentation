/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.caching;

import com.blogspot.mydailyjava.weaklockfree.AbstractWeakConcurrentMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Weak concurrent map that is very similar to weaklockfree's WeakConcurrentMap but supports
 * computeIfAbsent and takes advantage of some other features of modern Java.
 */
final class Java8WeakConcurrentMap<K, V>
    extends AbstractWeakConcurrentMap<K, V, Java8WeakConcurrentMap.LookupKey<K>> {

  /**
   * Lookup keys are cached thread-locally to avoid allocations on lookups. This is beneficial as
   * the JIT unfortunately can't reliably replace the {@link LookupKey} allocation with stack
   * allocations, even though the {@link LookupKey} does not escape.
   */
  private static final ThreadLocal<LookupKey<?>> LOOKUP_KEY_CACHE =
      ThreadLocal.withInitial(LookupKey::new);

  private static final AtomicLong ID = new AtomicLong();

  private final ConcurrentMap<WeakKey<K>, V> map;

  Java8WeakConcurrentMap(ConcurrentMap<WeakKey<K>, V> map) {
    super(map);
    this.map = map;

    Thread thread = new Thread(this);
    thread.setName("weak-ref-cleaner-" + ID.getAndIncrement());
    thread.setPriority(Thread.MIN_PRIORITY);
    thread.setDaemon(true);
    thread.start();
  }

  // computeIfAbsent is strongly typed but the weak map relies on using LookupKey instead of WeakKey
  @SuppressWarnings({"rawtypes, unchecked"})
  V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    if (key == null) {
      throw new NullPointerException();
    }

    try (LookupKey<K> lookupKey = getLookupKey(key)) {
      return (V)
          ((ConcurrentMap) map).computeIfAbsent(lookupKey, unused -> mappingFunction.apply(key));
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  protected LookupKey<K> getLookupKey(K key) {
    LookupKey<K> lookupKey = (LookupKey<K>) LOOKUP_KEY_CACHE.get();
    return lookupKey.withValue(key);
  }

  @Override
  protected void resetLookupKey(LookupKey<K> lookupKey) {
    lookupKey.close();
  }

  int size() {
    return map.size();
  }

  /*
   * A lookup key must only be used for looking up instances within a map. For this to work, it implements an identical contract for
   * hash code and equals as the WeakKey implementation. At the same time, the lookup key implementation does not extend WeakReference
   * and avoids the overhead that a weak reference implies.
   */
  static final class LookupKey<K> implements AutoCloseable {

    private K key;
    private int hashCode;

    LookupKey<K> withValue(K key) {
      this.key = key;
      hashCode = System.identityHashCode(key);
      return this;
    }

    /** Failing to reset a lookup key can lead to memory leaks as the key is strongly referenced. */
    @Override
    public void close() {
      key = null;
      hashCode = 0;
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof LookupKey<?>) {
        return ((LookupKey<?>) other).key == key;
      } else {
        return ((AbstractWeakConcurrentMap.WeakKey<?>) other).get() == key;
      }
    }

    @Override
    public int hashCode() {
      return hashCode;
    }
  }
}
