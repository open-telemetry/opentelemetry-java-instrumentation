package datadog.trace.bootstrap;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class WeakMapManager {
  private static final long CLEAN_FREQUENCY_SECONDS = 1;
  private static final List<WeakConcurrentMap> maps = new CopyOnWriteArrayList<>();

  private static final ThreadFactory weakMapCleanerThreadFactory =
      new ThreadFactory() {
        @Override
        public Thread newThread(final Runnable r) {
          final Thread thread = new Thread(r, "dd-weak-ref-cleaner");
          thread.setDaemon(true);
          thread.setPriority(Thread.MIN_PRIORITY);
          return thread;
        }
      };

  private static final ScheduledExecutorService cleaner =
      Executors.newScheduledThreadPool(1, weakMapCleanerThreadFactory);

  private static final Runnable runnable = new Cleaner();

  static {
    cleaner.scheduleAtFixedRate(
        runnable, CLEAN_FREQUENCY_SECONDS, CLEAN_FREQUENCY_SECONDS, TimeUnit.SECONDS);

    try {
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread() {
                @Override
                public void run() {
                  try {
                    cleaner.shutdownNow();
                    cleaner.awaitTermination(5, TimeUnit.SECONDS);
                  } catch (final InterruptedException e) {
                    // Don't bother waiting then...
                  }
                }
              });
    } catch (final IllegalStateException ex) {
      // The JVM is already shutting down.
    }
  }

  public static <K, V> WeakConcurrentMap<K, V> newWeakMap() {
    final WeakConcurrentMap<K, V> map = new WeakConcurrentMap<>(false);
    maps.add(map);
    return map;
  }

  public static void cleanMaps() {
    for (final WeakConcurrentMap map : maps) {
      map.expungeStaleEntries();
    }
  }

  private static class Cleaner implements Runnable {

    @Override
    public void run() {
      cleanMaps();
    }
  }
}
