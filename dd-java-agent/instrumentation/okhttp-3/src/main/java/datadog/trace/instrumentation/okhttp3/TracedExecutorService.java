package datadog.trace.instrumentation.okhttp3;

import io.opentracing.Tracer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** @author Pavol Loffay */
public class TracedExecutorService extends TracedExecutor implements ExecutorService {

  private final ExecutorService delegate;

  public TracedExecutorService(final ExecutorService delegate, final Tracer tracer) {
    super(delegate, tracer);
    this.delegate = delegate;
  }

  @Override
  public void shutdown() {
    delegate.shutdown();
  }

  @Override
  public List<Runnable> shutdownNow() {
    return delegate.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return delegate.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  @Override
  public boolean awaitTermination(final long l, final TimeUnit timeUnit)
      throws InterruptedException {
    return delegate.awaitTermination(l, timeUnit);
  }

  @Override
  public <T> Future<T> submit(final Callable<T> callable) {
    return delegate.submit(
        tracer.activeSpan() == null ? callable : new TracedCallable<T>(callable, tracer));
  }

  @Override
  public <T> Future<T> submit(final Runnable runnable, final T t) {
    return delegate.submit(
        tracer.activeSpan() == null ? runnable : new TracedRunnable(runnable, tracer), t);
  }

  @Override
  public Future<?> submit(final Runnable runnable) {
    return delegate.submit(
        tracer.activeSpan() == null ? runnable : new TracedRunnable(runnable, tracer));
  }

  @Override
  public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> collection)
      throws InterruptedException {
    return delegate.invokeAll(toTraced(collection));
  }

  @Override
  public <T> List<Future<T>> invokeAll(
      final Collection<? extends Callable<T>> collection, final long l, final TimeUnit timeUnit)
      throws InterruptedException {
    return delegate.invokeAll(toTraced(collection), l, timeUnit);
  }

  @Override
  public <T> T invokeAny(final Collection<? extends Callable<T>> collection)
      throws InterruptedException, ExecutionException {
    return delegate.invokeAny(toTraced(collection));
  }

  @Override
  public <T> T invokeAny(
      final Collection<? extends Callable<T>> collection, final long l, final TimeUnit timeUnit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return delegate.invokeAny(toTraced(collection), l, timeUnit);
  }

  private <T> Collection<? extends Callable<T>> toTraced(
      final Collection<? extends Callable<T>> delegate) {
    final List<Callable<T>> tracedCallables = new ArrayList<>(delegate.size());

    for (final Callable<T> callable : delegate) {
      tracedCallables.add(
          tracer.activeSpan() == null ? callable : new TracedCallable<T>(callable, tracer));
    }

    return tracedCallables;
  }
}
