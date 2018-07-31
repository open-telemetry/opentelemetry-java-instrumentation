package datadog.trace.instrumentation.spymemcached;

import io.opentracing.Span;
import io.opentracing.Tracer;
import java.util.concurrent.ExecutionException;
import net.spy.memcached.internal.BulkGetFuture;

public class BulkGetCompletionListener extends CompletionListener<BulkGetFuture<?>>
    implements net.spy.memcached.internal.BulkGetCompletionListener {
  public BulkGetCompletionListener(final Tracer tracer, final String methodName) {
    super(tracer, methodName, true);
  }

  @Override
  public void onComplete(final BulkGetFuture<?> future) {
    closeAsyncSpan(future);
  }

  @Override
  protected void processResult(final Span span, final BulkGetFuture<?> future)
      throws ExecutionException, InterruptedException {
    /*
    Note: for now we do not have an affective way of representing results of bulk operations,
    i.e. we cannot day that we got 4 hits out of 10. So we will just ignore results for now.
    */
    future.get();
  }
}
