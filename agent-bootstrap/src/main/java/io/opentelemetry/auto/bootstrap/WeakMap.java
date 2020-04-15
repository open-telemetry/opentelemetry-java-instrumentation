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
package io.opentelemetry.auto.bootstrap;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

public interface WeakMap<K, V> {

  int size();

  boolean containsKey(K target);

  V get(K key);

  void put(K key, V value);

  void putIfAbsent(K key, V value);

  V computeIfAbsent(K key, ValueSupplier<? super K, ? extends V> supplier);

  @Slf4j
  class Provider {
    private static final AtomicReference<Implementation> provider =
        new AtomicReference<>(Implementation.DEFAULT);

    public static void registerIfAbsent(final Implementation provider) {
      if (provider != null && provider != Implementation.DEFAULT) {
        if (Provider.provider.compareAndSet(Implementation.DEFAULT, provider)) {
          log.debug("Weak map provider set to {}", provider);
        }
      }
    }

    public static boolean isProviderRegistered() {
      return provider.get() != Implementation.DEFAULT;
    }

    public static <K, V> WeakMap<K, V> newWeakMap() {
      return provider.get().get();
    }
  }

  interface Implementation {
    <K, V> WeakMap<K, V> get();

    Implementation DEFAULT = new Default();

    @Slf4j
    class Default implements Implementation {

      @Override
      public <K, V> WeakMap<K, V> get() {
        log.warn("WeakMap.Supplier not registered. Returning a synchronized WeakHashMap.");
        return new MapAdapter<>(Collections.synchronizedMap(new WeakHashMap<K, V>()));
      }
    }
  }

  /**
   * Supplies the value to be stored and it is called only when a value does not exists yet in the
   * registry.
   */
  interface ValueSupplier<K, V> {
    V get(K key);
  }

  class MapAdapter<K, V> implements WeakMap<K, V> {
    private final Map<K, V> map;

    public MapAdapter(final Map<K, V> map) {
      this.map = map;
    }

    @Override
    public int size() {
      return map.size();
    }

    @Override
    public boolean containsKey(final K key) {
      return map.containsKey(key);
    }

    @Override
    public V get(final K key) {
      return map.get(key);
    }

    @Override
    public void put(final K key, final V value) {
      map.put(key, value);
    }

    @Override
    public void putIfAbsent(final K key, final V value) {
      // We can't use putIfAbsent since it was added in 1.8.
      // As a result, we must use double check locking.
      if (!map.containsKey(key)) {
        synchronized (this) {
          if (!map.containsKey(key)) {
            map.put(key, value);
          }
        }
      }
    }

    @Override
    public V computeIfAbsent(final K key, final ValueSupplier<? super K, ? extends V> supplier) {
      // We can't use computeIfAbsent since it was added in 1.8.
      if (map.containsKey(key)) {
        return map.get(key);
      }

      synchronized (this) {
        if (map.containsKey(key)) {
          return map.get(key);
        } else {
          final V value = supplier.get(key);

          map.put(key, value);
          return value;
        }
      }
    }

    @Override
    public String toString() {
      return map.toString();
    }
  }
}
