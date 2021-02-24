/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import com.google.common.collect.MapMaker;
import io.opentelemetry.javaagent.instrumentation.api.WeakMap;
import java.util.concurrent.TimeUnit;

class WeakMapSuppliers {
  // Comparison with using WeakConcurrentMap vs Guava's implementation:
  // Cleaning:
  // * `WeakConcurrentMap`: centralized but we have to maintain out own code and thread for it
  // * `Guava`: inline on application's thread, with constant max delay
  // Jar Size:
  // * `WeakConcurrentMap`: small
  // * `Guava`: large, but we may use other features, like immutable collections - and we already
  //          ship Guava as part of distribution now, so using Guava for this doesn't increase size.
  // Must go on bootstrap classpath:
  // * `WeakConcurrentMap`: version conflict is unlikely, so we can directly inject for now
  // * `Guava`: need to implement shadow copy (might eventually be necessary for other dependencies)
  // Used by other javaagents for similar purposes:
  // * `WeakConcurrentMap`: anecdotally used by other agents
  // * `Guava`: specifically agent use is unknown at the moment, but Guava is a well known library
  //            backed by big company with many-many users

  /**
   * Provides instances of {@link WeakConcurrentMap} and retains weak reference to them to allow a
   * single thread to clean void weak references out for all instances. Cleaning is done every
   * second.
   */
  static class WeakConcurrent implements WeakMap.Implementation {

    // Visible for testing
    static final long CLEAN_FREQUENCY_SECONDS = 1;

    @Override
    public <K, V> WeakMap<K, V> get() {
      WeakConcurrentMap<K, V> map = new WeakConcurrentMap<>(false, true);
      CommonTaskExecutor.INSTANCE.scheduleAtFixedRate(
          MapCleaningTask.INSTANCE,
          map,
          CLEAN_FREQUENCY_SECONDS,
          CLEAN_FREQUENCY_SECONDS,
          TimeUnit.SECONDS,
          "cleaner for " + map);
      return new Adapter<>(map);
    }

    // Important to use explicit class to avoid implicit hard references to target
    private static class MapCleaningTask implements CommonTaskExecutor.Task<WeakConcurrentMap> {

      static final MapCleaningTask INSTANCE = new MapCleaningTask();

      @Override
      public void run(WeakConcurrentMap target) {
        target.expungeStaleEntries();
      }
    }

    private static class Adapter<K, V> implements WeakMap<K, V> {
      private final WeakConcurrentMap<K, V> map;

      private Adapter(WeakConcurrentMap<K, V> map) {
        this.map = map;
      }

      @Override
      public int size() {
        return map.approximateSize();
      }

      @Override
      public boolean containsKey(K key) {
        return map.containsKey(key);
      }

      @Override
      public V get(K key) {
        return map.get(key);
      }

      @Override
      public void put(K key, V value) {
        map.put(key, value);
      }

      @Override
      public void putIfAbsent(K key, V value) {
        map.putIfAbsent(key, value);
      }

      @Override
      public V computeIfAbsent(K key, ValueSupplier<? super K, ? extends V> supplier) {
        if (map.containsKey(key)) {
          return map.get(key);
        }

        synchronized (this) {
          if (map.containsKey(key)) {
            return map.get(key);
          } else {
            V value = supplier.get(key);

            map.put(key, value);
            return value;
          }
        }
      }

      @Override
      public V remove(K key) {
        return map.remove(key);
      }
    }

    static class Inline implements WeakMap.Implementation {

      @Override
      public <K, V> WeakMap<K, V> get() {
        return new Adapter<>(new WeakConcurrentMap.WithInlinedExpunction<K, V>());
      }
    }
  }

  static class Guava implements WeakMap.Implementation {

    @Override
    public <K, V> WeakMap<K, V> get() {
      return new WeakMap.MapAdapter<>(new MapMaker().weakKeys().<K, V>makeMap());
    }

    public <K, V> WeakMap<K, V> get(int concurrencyLevel) {
      return new WeakMap.MapAdapter<>(
          new MapMaker().concurrencyLevel(concurrencyLevel).weakKeys().<K, V>makeMap());
    }
  }
}
