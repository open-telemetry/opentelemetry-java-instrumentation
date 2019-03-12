package datadog.trace.instrumentation.spymemcached;

import io.opentracing.Span;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import net.spy.memcached.MemcachedConnection;

@Slf4j
public class SyncCompletionListener extends CompletionListener<Void> {
  public SyncCompletionListener(final MemcachedConnection connection, final String methodName) {
    super(connection, methodName);
  }

  @Override
  protected void processResult(final Span span, final Void future)
      throws ExecutionException, InterruptedException {
    log.error("processResult was called on SyncCompletionListener. This should never happen. ");
  }

  public void done(final Throwable thrown) {
    closeSyncSpan(thrown);
  }
}
