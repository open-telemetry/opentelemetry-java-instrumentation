package datadog.trace.bootstrap;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;

public interface WeakMap<K, V> {

  int size();

  boolean containsKey(K target);

  V get(K key);

  void put(K key, V value);

  class Provider {
    private static final AtomicReference<Supplier> provider =
        new AtomicReference<>(Supplier.DEFAULT);

    /* The interface would be better defined as a Supplier, because we don't want throw an exception,
     * but that wasn't introduced until Java 8 and we must be compatible with 7.
     */
    public static void registerIfAbsent(final Supplier provider) {
      if (provider != null && provider != Supplier.DEFAULT) {
        Provider.provider.compareAndSet(Supplier.DEFAULT, provider);
      }
    }

    public static <K, V> WeakMap<K, V> newWeakMap() {
      return provider.get().get();
    }
  }

  interface Supplier {
    <K, V> WeakMap<K, V> get();

    Supplier DEFAULT = new Default();

    class Default implements Supplier {

      @Override
      public <K, V> WeakMap<K, V> get() {
        return new Adapter<>(Collections.synchronizedMap(new WeakHashMap<K, V>()));
      }

      private static class Adapter<K, V> implements WeakMap<K, V> {
        private final Map<K, V> map;

        private Adapter(final Map<K, V> map) {
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
      }
    }
  }
}
