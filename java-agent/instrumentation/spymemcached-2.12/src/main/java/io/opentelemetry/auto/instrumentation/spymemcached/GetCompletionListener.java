package io.opentelemetry.auto.instrumentation.spymemcached;

import java.util.concurrent.ExecutionException;

import io.opentelemetry.trace.Span;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.internal.GetFuture;

public class GetCompletionListener extends CompletionListener<GetFuture<?>>
    implements net.spy.memcached.internal.GetCompletionListener {
  public GetCompletionListener(final MemcachedConnection connection, final String methodName) {
    super(connection, methodName);
  }

  @Override
  public void onComplete(final GetFuture<?> future) {
    closeAsyncSpan(future);
  }

  @Override
  protected void processResult(final Span span, final GetFuture<?> future)
      throws ExecutionException, InterruptedException {
    final Object result = future.get();
    setResultTag(span, result != null);
  }
}
