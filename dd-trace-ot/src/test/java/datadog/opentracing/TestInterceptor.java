package datadog.opentracing;

import com.google.auto.service.AutoService;
import datadog.trace.api.interceptor.MutableSpan;
import datadog.trace.api.interceptor.TraceInterceptor;
import java.util.Collection;

@AutoService(TraceInterceptor.class)
public class TestInterceptor implements TraceInterceptor {
  public volatile int priority = 0;

  @Override
  public Collection<? extends MutableSpan> onTraceComplete(
      final Collection<? extends MutableSpan> trace) {
    return trace;
  }

  @Override
  public int priority() {
    return priority;
  }
}
