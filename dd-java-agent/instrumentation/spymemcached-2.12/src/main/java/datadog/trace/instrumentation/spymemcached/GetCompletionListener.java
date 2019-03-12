package datadog.trace.instrumentation.spymemcached;

import io.opentracing.Span;
import java.util.concurrent.ExecutionException;
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
