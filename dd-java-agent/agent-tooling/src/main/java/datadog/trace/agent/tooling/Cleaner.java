package datadog.trace.agent.tooling;

import datadog.common.exec.CommonTaskExecutor;
import datadog.common.exec.CommonTaskExecutor.Task;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class Cleaner {

  <T> void scheduleCleaning(
      final T target, final Adapter<T> adapter, final long frequency, final TimeUnit unit) {
    CommonTaskExecutor.INSTANCE.scheduleAtFixedRate(
        new CleaningTask(adapter), target, frequency, frequency, unit, "cleaner for " + target);
  }

  // Important to use explicit class to avoid implicit hard references to target
  private static class CleaningTask<T> implements Task<T> {
    private final Adapter<T> adapter;

    public CleaningTask(final Adapter<T> adapter) {
      this.adapter = adapter;
    }

    @Override
    public void run(final T target) {
      adapter.clean(target);
    }
  }

  public interface Adapter<T> {
    void clean(T target);
  }
}
