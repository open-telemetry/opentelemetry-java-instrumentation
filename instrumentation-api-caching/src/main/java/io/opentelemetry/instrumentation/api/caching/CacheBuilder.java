/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.caching;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.Executor;

/** A builder of {@link Cache}. */
public final class CacheBuilder<K, V> {

  private final Caffeine<?, ?> caffeine = Caffeine.newBuilder();

  /** Sets the maximum size of the cache. */
  public CacheBuilder<K, V> setMaximumSize(long maximumSize) {
    caffeine.maximumSize(maximumSize);
    return this;
  }

  /**
   * Sets that keys should be referenced weakly. If used, keys will use identity comparison, not
   * {@link Object#equals(Object)}.
   */
  public CacheBuilder<K, V> setWeakKeys() {
    caffeine.weakKeys();
    return this;
  }

  // Visible for testing
  CacheBuilder<K, V> setExecutor(Executor executor) {
    caffeine.executor(executor);
    return this;
  }

  /** Returns a new {@link Cache} with the settings of this {@link CacheBuilder}. */
  public Cache<K, V> build() {
    @SuppressWarnings("unchecked")
    com.github.benmanes.caffeine.cache.Cache<K, V> delegate =
        (com.github.benmanes.caffeine.cache.Cache<K, V>) caffeine.build();
    return new CaffeineCache<>(delegate);
  }

  CacheBuilder() {}
}
