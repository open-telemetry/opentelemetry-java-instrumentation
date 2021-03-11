/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.caching;

import java.util.Map;
import java.util.function.Function;

/** A cache from keys to values. */
public interface Cache<K, V> {

  /** Returns a new {@link CacheBuilder} to configure a {@link Cache}. */
  static <K, V> CacheBuilder<K, V> newBuilder() {
    return new CacheBuilder<>();
  }

  /**
   * Returns the cached value associated with the provided {@code key}. If no value is cached yet,
   * computes the value using {@code mappingFunction}, stores the result, and returns it.
   */
  V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction);

  /**
   * Returns a view of this {@link Cache} as a {@link Map}. All operations on the map are reflected
   * in the {@link Cache} and vice-versa.
   */
  Map<K, V> asMap();
}
