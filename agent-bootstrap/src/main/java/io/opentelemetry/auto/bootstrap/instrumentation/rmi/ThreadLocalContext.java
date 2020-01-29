package io.opentelemetry.auto.bootstrap.instrumentation.rmi;

import io.opentelemetry.trace.SpanContext;

public class ThreadLocalContext {
  public static final ThreadLocalContext THREAD_LOCAL_CONTEXT = new ThreadLocalContext();
  private final ThreadLocal<SpanContext> local;

  public ThreadLocalContext() {
    local = new ThreadLocal<>();
  }

  public void set(final SpanContext context) {
    local.set(context);
  }

  public SpanContext getAndResetContext() {
    final SpanContext context = local.get();
    if (context != null) {
      local.remove();
    }
    return context;
  }
}
