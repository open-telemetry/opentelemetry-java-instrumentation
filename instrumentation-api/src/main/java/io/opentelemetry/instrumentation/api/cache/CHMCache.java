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

import java.util.concurrent.ConcurrentHashMap;

final class CHMCache<K, V> implements Cache<K, V> {

  private final ConcurrentHashMap<K, V> chm;

  public CHMCache(final int initialCapacity) {
    this.chm = new ConcurrentHashMap<>(initialCapacity);
  }

  @Override
  public V computeIfAbsent(K key, Function<K, ? extends V> func) {
    if (null == key) {
      return null;
    }
    V value = chm.get(key);
    if (null == value) {
      value = func.apply(key);
      V winner = chm.putIfAbsent(key, value);
      if (null != winner) {
        value = winner;
      }
    }
    return value;
  }
}
