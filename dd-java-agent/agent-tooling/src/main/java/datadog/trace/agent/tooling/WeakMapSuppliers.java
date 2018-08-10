package datadog.trace.agent.tooling;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.MapMaker;
import datadog.trace.bootstrap.WeakMap;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
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
  static class WeakConcurrent implements WeakMap.Supplier {

    private static final long SHUTDOWN_WAIT_SECONDS = 5;

    @VisibleForTesting static final long CLEAN_FREQUENCY_SECONDS = 1;

    private static final ThreadFactory THREAD_FACTORY =
        new ThreadFactory() {
          @Override
          public Thread newThread(final Runnable r) {
            final Thread thread = new Thread(r, "dd-weak-ref-cleaner");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
          }
        };

    private final ScheduledExecutorService cleanerExecutorService;

    private final Queue<WeakReference<WeakConcurrentMap>> suppliedMaps =
        new ConcurrentLinkedQueue<>();

    private final Runnable runnable =
        new Runnable() {
          @Override
          public void run() {
            for (final Iterator<WeakReference<WeakConcurrentMap>> iterator =
                    suppliedMaps.iterator();
                iterator.hasNext(); ) {
              final WeakConcurrentMap map = iterator.next().get();
              if (map == null) {
                iterator.remove();
              } else {
                map.expungeStaleEntries();
              }
            }
          }
        };

    WeakConcurrent() {
      cleanerExecutorService = Executors.newScheduledThreadPool(1, THREAD_FACTORY);
      cleanerExecutorService.scheduleAtFixedRate(
          runnable, CLEAN_FREQUENCY_SECONDS, CLEAN_FREQUENCY_SECONDS, TimeUnit.SECONDS);

      try {
        Runtime.getRuntime()
            .addShutdownHook(
                new Thread() {
                  @Override
                  public void run() {
                    try {
                      cleanerExecutorService.shutdownNow();
                      cleanerExecutorService.awaitTermination(
                          SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
                    } catch (final InterruptedException e) {
                      // Don't bother waiting then...
                    }
                  }
                });
      } catch (final IllegalStateException ex) {
        // The JVM is already shutting down.
      }
    }

    @Override
    public <K, V> WeakMap<K, V> get() {
      final WeakConcurrentMap<K, V> map = new WeakConcurrentMap<>(false);
      suppliedMaps.add(new WeakReference<WeakConcurrentMap>(map));
      return new Adapter<>(map);
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
    }

    static class Inline implements WeakMap.Supplier {

      @Override
      public <K, V> WeakMap<K, V> get() {
        return new Adapter<>(new WeakConcurrentMap.WithInlinedExpunction<K, V>());
      }
    }
  }

  static class Guava implements WeakMap.Supplier {

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
