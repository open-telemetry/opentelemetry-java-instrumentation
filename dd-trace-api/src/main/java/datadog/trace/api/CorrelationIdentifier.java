package datadog.trace.api;

/**
 * Utility class to access the active trace and span ids.
 *
 * <p>Intended for use with MDC frameworks.
 */
public class CorrelationIdentifier {
  private static final String TRACE_ID_KEY = "dd.trace_id";
  private static final String SPAN_ID_KEY = "dd.span_id";

  /** @return The trace-id key to use with datadog logs integration */
  public static String getTraceIdKey() {
    return TRACE_ID_KEY;
  }

  /** @return The span-id key to use with datadog logs integration */
  public static String getSpanIdKey() {
    return SPAN_ID_KEY;
  }

  public static String getTraceId() {
    return GlobalTracer.get().getTraceId();
  }

  public static String getSpanId() {
    return GlobalTracer.get().getSpanId();
  }
}
