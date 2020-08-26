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

package io.opentelemetry.instrumentation.api.cache;

public final class Caches {

  /**
   * Creates a cache which cannot grow beyond a fixed capacity. Useful for caching relationships
   * between low cardinality but potentially unbounded keys with values, without risking using
   * unbounded space.
   *
   * @param capacity the cache's fixed capacity
   * @param <K> the key type
   * @param <V> the value type
   * @return the value associated with the key
   */
  public static <K, V> Cache<K, V> newFixedSizeCache(final int capacity) {
    return new FixedSizeCache<>(capacity);
  }

  /**
   * Creates a memoization of an association. Useful for creating an association between an
   * implicitly bounded set of keys and values, where the nature of the keys prevents unbounded
   * space usage.
   *
   * @param initialCapacity the initial capacity. To avoid resizing, should be larger than the total
   *     number of keys.
   * @param <K> the key type
   * @param <V> the value type
   * @return the value associated with the key
   */
  public static <K, V> Cache<K, V> newUnboundedCache(final int initialCapacity) {
    return new CHMCache<>(initialCapacity);
  }
}
