package datadog.trace.instrumentation.spymemcached;

import io.opentracing.Tracer;

public class SyncCompletionListener extends CompletionListener<Void> {
  public SyncCompletionListener(Tracer tracer, String methodName) {
    super(tracer, methodName, false);
  }

  public void done(Throwable thrown) {
    closeSyncSpan(thrown);
  }
}
