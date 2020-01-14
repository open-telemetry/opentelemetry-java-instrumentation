package io.opentelemetry.auto.tooling.log;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.trace.Tracer;
import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;

/**
 * A scope listener that receives the MDC/ThreadContext put and receive methods and update the trace
 * and span reference anytime a new scope is activated or closed.
 */
@Slf4j
public class LogContextScopeListener {
  private static final String TRACE_ID_KEY = "ot.trace_id";
  private static final String SPAN_ID_KEY = "ot.span_id";

  /** A reference to the log context method that sets a new attribute in the log context */
  private final Method putMethod;

  /** A reference to the log context method that removes an attribute from the log context */
  private final Method removeMethod;

  final Tracer tracer = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  public LogContextScopeListener(final Method putMethod, final Method removeMethod) {
    this.putMethod = putMethod;
    this.removeMethod = removeMethod;
  }

  public void afterScopeActivated() {
    try {
      putMethod.invoke(
          null, TRACE_ID_KEY, tracer.getCurrentSpan().getContext().getTraceId().toLowerBase16());
      putMethod.invoke(
          null, SPAN_ID_KEY, tracer.getCurrentSpan().getContext().getSpanId().toLowerBase16());
    } catch (final Exception e) {
      log.debug("Exception setting log context context", e);
    }
  }

  public void afterScopeClosed() {
    try {
      removeMethod.invoke(null, TRACE_ID_KEY);
      removeMethod.invoke(null, SPAN_ID_KEY);
    } catch (final Exception e) {
      log.debug("Exception removing log context context", e);
    }
  }
}
