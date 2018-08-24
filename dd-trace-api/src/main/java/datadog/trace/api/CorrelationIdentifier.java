package datadog.trace.api;

import datadog.trace.context.TracerBridge;

/**
 * Utility class to access the active trace and span ids.
 *
 * <p>Intended for use with MDC frameworks.
 */
public class CorrelationIdentifier {
  public static String getTraceId() {
    return TracerBridge.get().getTraceId();
  }

  public static String getSpanId() {
    return TracerBridge.get().getSpanId();
  }

  public interface Provider {
    String getTraceId();

    String getSpanId();

    Provider NO_OP =
        new Provider() {
          @Override
          public String getTraceId() {
            return "0";
          }

          @Override
          public String getSpanId() {
            return "0";
          }
        };
  }
}
