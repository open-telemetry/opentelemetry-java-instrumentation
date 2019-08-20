package datadog.trace.agent.tooling;

import java.lang.ref.WeakReference;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class Cleaner {
  private static final long SHUTDOWN_WAIT_SECONDS = 5;

  private static final ThreadFactory THREAD_FACTORY =
      new ThreadFactory() {
        @Override
        public Thread newThread(final Runnable r) {
          final Thread thread = new Thread(r, "dd-cleaner");
          thread.setDaemon(true);
          thread.setPriority(Thread.MIN_PRIORITY);
          return thread;
        }
      };

  private final ScheduledThreadPoolExecutor cleanerService;
  private final Thread shutdownCallback;

  Cleaner() {
    cleanerService = new ScheduledThreadPoolExecutor(1, THREAD_FACTORY);
    cleanerService.setRemoveOnCancelPolicy(true);
    shutdownCallback = new ShutdownCallback(cleanerService);
    try {
      Runtime.getRuntime().addShutdownHook(shutdownCallback);
    } catch (final IllegalStateException ex) {
      // The JVM is already shutting down.
    }
  }

  <T> void scheduleCleaning(
      final T target, final Adapter<T> adapter, final long frequency, final TimeUnit unit) {
    final CleanupRunnable<T> command = new CleanupRunnable<>(target, adapter);
    if (cleanerService.isShutdown()) {
      log.warn("Cleaning scheduled but cleaner is shutdown. Target won't be cleaned {}", target);
    } else {
      try {
        // Schedule job and save future to allow job to be canceled if target is GC'd.
        command.setFuture(cleanerService.scheduleAtFixedRate(command, frequency, frequency, unit));
      } catch (final RejectedExecutionException e) {
        log.warn("Cleaning task rejected. Target won't be cleaned {}", target);
      }
    }
  }

  private void stop() {
    cleanerService.shutdownNow();
    Runtime.getRuntime().removeShutdownHook(shutdownCallback);
  }

  @Override
  public void finalize() {
    // Do we really want this?
    stop();
  }

  public interface Adapter<T> {
    void clean(T target);
  }

  private static class CleanupRunnable<T> implements Runnable {
    private final WeakReference<T> target;
    private final Adapter<T> adapter;
    private volatile ScheduledFuture<?> future = null;

    private CleanupRunnable(final T target, final Adapter<T> adapter) {
      this.target = new WeakReference<>(target);
      this.adapter = adapter;
    }

    @Override
    public void run() {
      final T t = target.get();
      if (t != null) {
        adapter.clean(t);
      } else if (future != null) {
        future.cancel(false);
      }
    }

    public void setFuture(final ScheduledFuture<?> future) {
      this.future = future;
    }
  }

  private static final class ShutdownCallback extends Thread {

    private final ScheduledExecutorService executorService;

    private ShutdownCallback(final ScheduledExecutorService executorService) {
      this.executorService = executorService;
    }

    @Override
    public void run() {
      try {
        executorService.shutdownNow();
        executorService.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
      } catch (final InterruptedException e) {
        // Don't bother waiting then...
      }
    }
  }
}
