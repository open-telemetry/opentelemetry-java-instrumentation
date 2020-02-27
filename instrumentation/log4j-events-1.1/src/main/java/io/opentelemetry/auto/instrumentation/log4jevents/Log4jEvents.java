package io.opentelemetry.auto.instrumentation.log4jevents;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.config.Config;
import io.opentelemetry.trace.AttributeValue;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.log4j.Category;
import org.apache.log4j.Priority;

@Slf4j
public class Log4jEvents {

  private static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.log4j-events-1.1");

  // these constants are copied from org.apache.log4j.Priority and org.apache.log4j.Level because
  // Level was only introduced in 1.2, and then Level.TRACE was only introduced in 1.2.12
  private static final int OFF_INT = Integer.MAX_VALUE;
  private static final int FATAL_INT = 50000;
  private static final int ERROR_INT = 40000;
  private static final int WARN_INT = 30000;
  private static final int INFO_INT = 20000;
  private static final int DEBUG_INT = 10000;
  private static final int TRACE_INT = 5000;
  private static final int ALL_INT = Integer.MIN_VALUE;

  public static void capture(
      final Category logger, final Priority level, final Object message, final Throwable t) {

    if (level.toInt() < getThreshold()) {
      return;
    }
    final Span currentSpan = TRACER.getCurrentSpan();
    if (!currentSpan.getContext().isValid()) {
      return;
    }

    final Map<String, AttributeValue> attributes = new HashMap<>(t == null ? 2 : 3);
    attributes.put("level", newAttributeValue(level.toString()));
    attributes.put("loggerName", newAttributeValue(logger.getName()));
    if (t != null) {
      attributes.put("error.stack", newAttributeValue(toString(t)));
    }
    currentSpan.addEvent(String.valueOf(message), attributes);
  }

  private static AttributeValue newAttributeValue(final String stringValue) {
    return AttributeValue.stringAttributeValue(stringValue);
  }

  private static String toString(final Throwable t) {
    final StringWriter out = new StringWriter();
    t.printStackTrace(new PrintWriter(out));
    return out.toString();
  }

  private static int getThreshold() {
    final String level = Config.get().getLogCaptureThreshold();
    if (level == null) {
      return OFF_INT;
    }
    switch (level) {
      case "OFF":
        return OFF_INT;
      case "FATAL":
        return FATAL_INT;
      case "ERROR":
      case "SEVERE":
        return ERROR_INT;
      case "WARN":
      case "WARNING":
        return WARN_INT;
      case "INFO":
        return INFO_INT;
      case "CONFIG":
      case "DEBUG":
      case "FINE":
      case "FINER":
        return DEBUG_INT;
      case "TRACE":
      case "FINEST":
        return TRACE_INT;
      case "ALL":
        return ALL_INT;
      default:
        log.error("unexpected value for {}: {}", Config.LOG_CAPTURE_THRESHOLD, level);
        return OFF_INT;
    }
  }
}
