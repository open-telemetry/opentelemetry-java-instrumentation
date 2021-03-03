package io.opentelemetry.instrumentation.api.caching;

import com.github.benmanes.caffeine.cache.Caffeine;

public final class CacheBuilder {

  private final Caffeine<?, ?> caffeine = Caffeine.newBuilder();

  public CacheBuilder setMaximumSize(long maximumSize) {
    caffeine.maximumSize(maximumSize);
    return this;
  }

  public CacheBuilder setWeakKeys() {
    caffeine.weakKeys();
    return this;
  }

  public <K, V> Cache<K, V> build() {
    @SuppressWarnings("unchecked")
    com.github.benmanes.caffeine.cache.Cache<K, V> delegate =
        (com.github.benmanes.caffeine.cache.Cache<K, V>) caffeine.build();
    return new CaffeineCache<K, V>(delegate);
  }

  CacheBuilder() {}
}
