package datadog.trace.agent.tooling;

import datadog.common.exec.CommonTaskExecutor;
import java.lang.ref.WeakReference;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class Cleaner {

  <T> void scheduleCleaning(
      final T target, final Adapter<T> adapter, final long frequency, final TimeUnit unit) {
    final CleanupRunnable<T> command = new CleanupRunnable<>(target, adapter);
    if (CommonTaskExecutor.INSTANCE.isShutdown()) {
      log.warn(
          "Cleaning scheduled but task scheduler is shutdown. Target won't be cleaned {}", target);
    } else {
      try {
        // Schedule job and save future to allow job to be canceled if target is GC'd.
        command.setFuture(
            CommonTaskExecutor.INSTANCE.scheduleAtFixedRate(command, frequency, frequency, unit));
      } catch (final RejectedExecutionException e) {
        log.warn("Cleaning task rejected. Target won't be cleaned {}", target);
      }
    }
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
}
