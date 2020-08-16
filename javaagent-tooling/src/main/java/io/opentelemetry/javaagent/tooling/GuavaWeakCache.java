/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.javaagent.tooling;

import com.google.auto.service.AutoService;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.opentelemetry.javaagent.bootstrap.WeakCache;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * no null keys nor null values are permitted
 *
 * @param <K>
 * @param <V>
 */
public final class GuavaWeakCache<K, V> implements WeakCache<K, V> {

  private static final Logger log = LoggerFactory.getLogger(GuavaWeakCache.class);

  @AutoService(WeakCache.Provider.class)
  public static final class Provider<K, V> implements WeakCache.Provider<K, V> {
    private static final int CACHE_CONCURRENCY =
        Math.max(8, Runtime.getRuntime().availableProcessors());

    @Override
    public GuavaWeakCache<K, V> newWeakCache() {
      return new GuavaWeakCache(
          CacheBuilder.newBuilder()
              .weakKeys()
              .concurrencyLevel(CACHE_CONCURRENCY)
              .expireAfterAccess(10, TimeUnit.MINUTES)
              .build());
    }

    @Override
    public GuavaWeakCache<K, V> newWeakCache(final long maxSize) {
      return new GuavaWeakCache(
          CacheBuilder.newBuilder()
              .weakKeys()
              .maximumSize(maxSize)
              .concurrencyLevel(CACHE_CONCURRENCY)
              .expireAfterAccess(10, TimeUnit.MINUTES)
              .build());
    }
  }

  private final Cache<K, V> cache;

  private GuavaWeakCache(final Cache<K, V> cache) {
    this.cache = cache;
  }

  /**
   * @return null if key is not present
   * @param key
   */
  @Override
  public V getIfPresent(final K key) {
    return cache.getIfPresent(key);
  }

  @Override
  public V getIfPresentOrCompute(final K key, final Callable<? extends V> loader) {
    V v = cache.getIfPresent(key);
    if (v != null) {
      return v;
    }
    try {
      return cache.get(key, loader);
    } catch (final ExecutionException e) {
      log.error("Can't get value from cache", e);
    }
    return null;
  }

  @Override
  public V get(final K key, final Callable<? extends V> loader) {
    try {
      return cache.get(key, loader);
    } catch (final ExecutionException e) {
      log.error("Can't get value from cache", e);
    }
    return null;
  }

  @Override
  public void put(final K key, final V value) {
    cache.put(key, value);
  }
}
