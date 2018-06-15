package datadog.trace.instrumentation.spymemcached;

import io.opentracing.Span;
import io.opentracing.Tracer;
import java.util.concurrent.ExecutionException;
import net.spy.memcached.internal.GetFuture;

public class GetCompletionListener extends CompletionListener<GetFuture<?>>
    implements net.spy.memcached.internal.GetCompletionListener {
  public GetCompletionListener(Tracer tracer, String methodName) {
    super(tracer, methodName, true);
  }

  @Override
  public void onComplete(GetFuture<?> future) {
    closeAsyncSpan(future);
  }

  @Override
  protected void processResult(Span span, GetFuture<?> future)
      throws ExecutionException, InterruptedException {
    Object result = future.get();
    setResultTag(span, result != null);
  }
}
