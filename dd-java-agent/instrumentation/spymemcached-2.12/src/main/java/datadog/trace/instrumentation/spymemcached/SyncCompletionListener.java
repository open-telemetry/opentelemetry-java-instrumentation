package datadog.trace.instrumentation.spymemcached;

import io.opentracing.Span;
import io.opentracing.Tracer;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SyncCompletionListener extends CompletionListener<Void> {
  public SyncCompletionListener(Tracer tracer, String methodName) {
    super(tracer, methodName, false);
  }

  @Override
  protected void processResult(Span span, Void future)
      throws ExecutionException, InterruptedException {
    log.error("processResult was called on SyncCompletionListener. This should never happen. ");
  }

  public void done(Throwable thrown) {
    closeSyncSpan(thrown);
  }
}
