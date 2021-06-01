/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.caching;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.qual.Nullable;

/** A builder of {@link Cache}. */
public final class CacheBuilder {

  private static final long UNSET = -1;

  private boolean weakKeys;
  private boolean weakValues;
  private long maximumSize = UNSET;
  @Nullable private Executor executor = null;

  /** Sets the maximum size of the cache. */
  public CacheBuilder setMaximumSize(long maximumSize) {
    this.maximumSize = maximumSize;
    return this;
  }

  /**
   * Sets that keys should be referenced weakly. If used, keys will use identity comparison, not
   * {@link Object#equals(Object)}.
   */
  public CacheBuilder setWeakKeys() {
    this.weakKeys = true;
    return this;
  }

  /** Sets that values should be referenced weakly. */
  public CacheBuilder setWeakValues() {
    this.weakValues = true;
    return this;
  }

  // Visible for testing
  CacheBuilder setExecutor(Executor executor) {
    this.executor = executor;
    return this;
  }

  /** Returns a new {@link Cache} with the settings of this {@link CacheBuilder}. */
  public <K, V> Cache<K, V> build() {
    if (weakKeys && !weakValues && maximumSize == UNSET) {
      return new WeakLockFreeCache<>();
    }
    Caffeine<?, ?> caffeine = Caffeine.newBuilder();
    if (weakKeys) {
      caffeine.weakKeys();
    }
    if (weakValues) {
      caffeine.weakValues();
    }
    if (maximumSize != UNSET) {
      caffeine.maximumSize(maximumSize);
    }
    if (executor != null) {
      caffeine.executor(executor);
    } else {
      caffeine.executor(Runnable::run);
    }
    @SuppressWarnings("unchecked")
    com.github.benmanes.caffeine.cache.Cache<K, V> delegate =
        (com.github.benmanes.caffeine.cache.Cache<K, V>) caffeine.build();
    return new CaffeineCache<>(delegate);
  }

  CacheBuilder() {}
}
