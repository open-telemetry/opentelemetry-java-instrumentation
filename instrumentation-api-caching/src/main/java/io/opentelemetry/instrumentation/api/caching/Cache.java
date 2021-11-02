/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.caching;

import java.util.function.Function;
import javax.annotation.Nullable;

/** A cache from keys to values. */
public interface Cache<K, V> {

  /** Returns a new {@link CacheBuilder} to configure a {@link Cache}. */
  static CacheBuilder builder() {
    return new CacheBuilder();
  }

  /**
   * Returns the cached value associated with the provided {@code key}. If no value is cached yet,
   * computes the value using {@code mappingFunction}, stores the result, and returns it.
   */
  V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction);

  /**
   * Returns the cached value associated with the provided {@code key} if present, or {@code null}
   * otherwise.
   */
  @Nullable
  V get(K key);

  /** Puts the {@code value} into the cache for the {@code key}. */
  void put(K key, V value);

  /** Removes a value for {@code key} if present. */
  void remove(K key);
}
