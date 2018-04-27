package datadog.trace.instrumentation.okhttp3;

import io.opentracing.Tracer;
import java.util.concurrent.Executor;

/**
 * Executor which propagates span from parent thread to submitted {@link Runnable}.
 *
 * @author Pavol Loffay
 */
public class TracedExecutor implements Executor {

  protected final Tracer tracer;
  private final Executor delegate;

  public TracedExecutor(final Executor executor, final Tracer tracer) {
    this.delegate = executor;
    this.tracer = tracer;
  }

  @Override
  public void execute(final Runnable runnable) {
    delegate.execute(tracer.activeSpan() == null ? runnable : new TracedRunnable(runnable, tracer));
  }
}
