package datadog.trace.bootstrap.instrumentation.java.concurrent;

import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;

/**
 * This is used to wrap lambda callables since currently we cannot instrument them
 *
 * <p>FIXME: We should remove this once https://github.com/raphw/byte-buddy/issues/558 is fixed
 */
@Slf4j
public final class CallableWrapper implements Callable {
  private final Callable callable;

  public CallableWrapper(final Callable callable) {
    this.callable = callable;
  }

  @Override
  public Object call() throws Exception {
    return callable.call();
  }

  public static Callable<?> wrapIfNeeded(final Callable<?> task) {
    if (task.getClass().getName().contains("/") && (!(task instanceof CallableWrapper))) {
      log.debug("Wrapping callable task {}", task);
      return new CallableWrapper(task);
    }
    return task;
  }
}
