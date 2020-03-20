package datadog.trace.bootstrap;

import java.util.concurrent.Callable;

public interface WeakCache<K, V> {
  interface Provider<K, V> {
    WeakCache<K, V> newWeakCache();

    WeakCache<K, V> newWeakCache(final long maxSize);
  }

  V getIfPresent(final K key);

  V getIfPresentOrCompute(final K key, final Callable<? extends V> loader);

  V get(final K key, final Callable<? extends V> loader);

  void put(final K key, final V value);
}
