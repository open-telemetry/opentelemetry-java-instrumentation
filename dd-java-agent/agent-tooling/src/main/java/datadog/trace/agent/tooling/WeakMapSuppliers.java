package datadog.trace.agent.tooling;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.MapMaker;
import datadog.trace.bootstrap.WeakMap;
import java.util.concurrent.TimeUnit;

class WeakMapSuppliers {
  // Comparison with using WeakConcurrentMap vs Guava's implementation:
  // Cleaning:
  // * `WeakConcurrentMap`: centralized but we have to maintain out own code and thread for it
  // * `Guava`: inline on application's thread, with constant max delay
  // Jar Size:
  // * `WeakConcurrentMap`: small
  // * `Guava`: large, but we may use other features, like immutable collections - and we already
  //          ship Guava as part of distribution now, so using Guava for this doesn’t increase size.
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

    @VisibleForTesting static final long CLEAN_FREQUENCY_SECONDS = 1;
    private final Cleaner cleaner;

    WeakConcurrent(final Cleaner cleaner) {
      this.cleaner = cleaner;
    }

    @Override
    public <K, V> WeakMap<K, V> get() {
      final WeakConcurrentMap<K, V> map = new WeakConcurrentMap<>(false);
      cleaner.scheduleCleaning(map, MapCleaner.CLEANER, CLEAN_FREQUENCY_SECONDS, TimeUnit.SECONDS);
      return new Adapter(map);
    }

    private static class MapCleaner implements Cleaner.Adapter<WeakConcurrentMap> {
      private static final MapCleaner CLEANER = new MapCleaner();

      @Override
      public void clean(final WeakConcurrentMap target) {
        target.expungeStaleEntries();
      }
    }

    private static class Adapter<K, V> implements WeakMap<K, V> {
      private final WeakConcurrentMap<K, V> map;

      private Adapter(final WeakConcurrentMap<K, V> map) {
        this.map = map;
      }

      @Override
      public int size() {
        return map.approximateSize();
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
        map.putIfAbsent(key, value);
      }

      @Override
      public V getOrCreate(final K key, final ValueSupplier<V> supplier) {
        if (!map.containsKey(key)) {
          synchronized (this) {
            if (!map.containsKey(key)) {
              map.put(key, supplier.get());
            }
          }
        }

        return map.get(key);
      }
    }

    static class Inline implements WeakMap.Implementation {

      @Override
      public <K, V> WeakMap<K, V> get() {
        return new Adapter(new WeakConcurrentMap.WithInlinedExpunction<K, V>());
      }
    }
  }

  static class Guava implements WeakMap.Implementation {

    @Override
    public <K, V> WeakMap<K, V> get() {
      return new WeakMap.MapAdapter<>(new MapMaker().weakKeys().<K, V>makeMap());
    }

    public <K, V> WeakMap<K, V> get(final int concurrencyLevel) {
      return new WeakMap.MapAdapter<>(
          new MapMaker().concurrencyLevel(concurrencyLevel).weakKeys().<K, V>makeMap());
    }
  }
}
