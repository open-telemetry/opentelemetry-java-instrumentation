package datadog.trace.api;

/**
 * Utility class to access the active trace and span ids.
 *
 * <p>Intended for use with MDC frameworks.
 */
public class CorrelationIdentifier {
  public static String getTraceId() {
    return GlobalTracer.get().getTraceId();
  }

  public static String getSpanId() {
    return GlobalTracer.get().getSpanId();
  }
}
