package datadog.trace.bootstrap;

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

  @Slf4j
  class Provider {
    private static final AtomicReference<Supplier> provider =
        new AtomicReference<>(Supplier.DEFAULT);

    public static void registerIfAbsent(final Supplier provider) {
      if (provider != null && provider != Supplier.DEFAULT) {
        if (Provider.provider.compareAndSet(Supplier.DEFAULT, provider)) {
          log.debug("Weak map provider set to {}", provider);
        }
      }
    }

    public static boolean isProviderRegistered() {
      return provider.get() != Supplier.DEFAULT;
    }

    public static <K, V> WeakMap<K, V> newWeakMap() {
      return provider.get().get();
    }
  }

  interface Supplier {
    <K, V> WeakMap<K, V> get();

    Supplier DEFAULT = new Default();

    @Slf4j
    class Default implements Supplier {

      @Override
      public <K, V> WeakMap<K, V> get() {
        log.warn("WeakMap.Supplier not registered. Returning a synchronized WeakHashMap.");
        return new MapAdapter<>(Collections.synchronizedMap(new WeakHashMap<K, V>()));
      }
    }
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
    public String toString() {
      return map.toString();
    }
  }
}
