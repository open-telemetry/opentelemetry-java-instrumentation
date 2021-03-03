package io.opentelemetry.instrumentation.api.caching;

import java.util.AbstractMap;
import java.util.function.Function;

final class CaffeineCache<K, V> implements Cache<K, V> {

  private final com.github.benmanes.caffeine.cache.Cache<K, V> delegate;

  CaffeineCache(com.github.benmanes.caffeine.cache.Cache<K, V> delegate) {
    this.delegate = delegate;
  }

  @Override
  public V computeIfAbsent(K key,
      Function<? super K, ? extends V> mappingFunction) {
    return delegate.get(key, mappingFunction);
  }

  // Visible for testing
  long estimatedSize() {
    AbstractMap
    return delegate.estimatedSize();
  }
}
