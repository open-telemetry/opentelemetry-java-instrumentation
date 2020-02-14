package datadog.opentracing;

import java.util.Map;

public interface LogsHandler {

  /**
   * Handles the log implementation in the Span.
   *
   * @param fields key:value log fields. Tracer implementations should support String, numeric, and
   *     boolean values; some may also support arbitrary Objects.
   */
  void log(Map<String, ?> fields);

  /**
   * Handles the log implementation in the Span.
   *
   * @param timestampMicroseconds The explicit timestamp for the log record. Must be greater than or
   *     equal to the Span's start timestamp.
   * @param fields key:value log fields. Tracer implementations should support String, numeric, and
   *     boolean values; some may also support arbitrary Objects.
   */
  void log(long timestampMicroseconds, Map<String, ?> fields);

  /**
   * Handles the log implementation in the Span..
   *
   * @param event the event value; often a stable identifier for a moment in the Span lifecycle
   */
  void log(String event);

  /**
   * Handles the log implementation in the Span.
   *
   * @param timestampMicroseconds The explicit timestamp for the log record. Must be greater than or
   *     equal to the Span's start timestamp.
   * @param event the event value; often a stable identifier for a moment in the Span lifecycle
   */
  void log(long timestampMicroseconds, String event);
}
