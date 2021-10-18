/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.caching;

import java.util.concurrent.Executor;
import javax.annotation.Nullable;

/** A builder of {@link Cache}. */
public final class CacheBuilder {

  private static final long UNSET = -1;
  private static final boolean USE_CAFFEINE_3 = Caffeine3Cache.available();

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
    CaffeineCache.Builder<K, V> caffeine =
        USE_CAFFEINE_3 ? new Caffeine3Cache.Builder<>() : new Caffeine2Cache.Builder<>();
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
    return caffeine.build();
  }

  CacheBuilder() {}
}
