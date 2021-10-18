/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.caching;

import io.opentelemetry.instrumentation.api.internal.shaded.caffeine3.cache.Caffeine;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

final class Caffeine3Cache<K, V> implements CaffeineCache<K, V> {

  private final io.opentelemetry.instrumentation.api.internal.shaded.caffeine3.cache.Cache<K, V>
      delegate;

  Caffeine3Cache(
      io.opentelemetry.instrumentation.api.internal.shaded.caffeine3.cache.Cache<K, V> delegate) {
    this.delegate = delegate;
  }

  public static boolean available() {
    try {
      Caffeine.class.getName();
      return true;
    } catch (UnsupportedClassVersionError exception) {
      // caffeine 3 requires jdk 11
      return false;
    }
  }

  @Override
  public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    return delegate.get(key, mappingFunction);
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
    delegate.invalidate(key);
  }

  // Visible for testing
  @Override
  public Set<K> keySet() {
    return delegate.asMap().keySet();
  }

  // Visible for testing
  @Override
  public void cleanup() {
    delegate.cleanUp();
  }

  static class Builder<K, V> implements CaffeineCache.Builder<K, V> {
    private final Caffeine<?, ?> caffeine = Caffeine.newBuilder();

    @Override
    public void weakKeys() {
      caffeine.weakKeys();
    }

    @Override
    public void weakValues() {
      caffeine.weakValues();
    }

    @Override
    public void maximumSize(@Nonnegative long maximumSize) {
      caffeine.maximumSize(maximumSize);
    }

    @Override
    public void executor(@Nonnull Executor executor) {
      caffeine.executor(executor);
    }

    @Override
    public Cache<K, V> build() {
      @SuppressWarnings("unchecked")
      io.opentelemetry.instrumentation.api.internal.shaded.caffeine3.cache.Cache<K, V> delegate =
          (io.opentelemetry.instrumentation.api.internal.shaded.caffeine3.cache.Cache<K, V>)
              caffeine.build();
      return new Caffeine3Cache<>(delegate);
    }
  }
}
