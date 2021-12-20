/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_16.internal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.appender.LogBuilder;
import io.opentelemetry.instrumentation.api.appender.Severity;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.message.Message;

public final class LogEventMapper {

  /**
   * Map the {@link LogEvent} data model onto the {@link LogBuilder}. Unmapped fields include:
   *
   * <ul>
   *   <li>Fully qualified class name - {@link LogEvent#getLoggerFqcn()}
   *   <li>Thread name - {@link LogEvent#getThreadName()}
   *   <li>Thread id - {@link LogEvent#getThreadId()}
   *   <li>Thread priority - {@link LogEvent#getThreadPriority()}
   *   <li>Marker - {@link LogEvent#getMarker()}
   *   <li>Nested diagnostic context - {@link LogEvent#getContextStack()}
   *   <li>Mapped diagnostic context - {@link LogEvent#getContextData()}
   * </ul>
   */
  public static void mapLogEvent(LogBuilder builder, LogEvent logEvent) {
    setBody(builder, logEvent.getMessage());
    setSeverity(builder, logEvent.getLevel());
    setThrowable(builder, logEvent.getThrown());
    setContext(builder);

    // time
    Instant instant = logEvent.getInstant();
    if (instant != null) {
      builder.setEpoch(
          TimeUnit.MILLISECONDS.toNanos(instant.getEpochMillisecond())
              + instant.getNanoOfMillisecond(),
          TimeUnit.NANOSECONDS);
    }
  }

  public static void setBody(LogBuilder builder, Message message) {
    if (message != null) {
      builder.setBody(message.getFormattedMessage());
    }
  }

  public static void setSeverity(LogBuilder builder, Level level) {
    if (level != null) {
      builder.setSeverity(levelToSeverity(level));
      builder.setSeverityText(level.name());
    }
  }

  public static void setThrowable(LogBuilder builder, Throwable throwable) {
    if (throwable != null) {
      AttributesBuilder attributes = Attributes.builder();

      // TODO (trask) extract method for recording exception into instrumentation-api-appender
      attributes.put(SemanticAttributes.EXCEPTION_TYPE, throwable.getClass().getName());
      attributes.put(SemanticAttributes.EXCEPTION_MESSAGE, throwable.getMessage());
      StringWriter writer = new StringWriter();
      throwable.printStackTrace(new PrintWriter(writer));
      attributes.put(SemanticAttributes.EXCEPTION_STACKTRACE, writer.toString());

      builder.setAttributes(attributes.build());
    }
  }

  public static void setContext(LogBuilder builder) {
    builder.setContext(Context.current());
  }

  private static Severity levelToSeverity(Level level) {
    switch (level.getStandardLevel()) {
      case ALL:
      case TRACE:
        return Severity.TRACE;
      case DEBUG:
        return Severity.DEBUG;
      case INFO:
        return Severity.INFO;
      case WARN:
        return Severity.WARN;
      case ERROR:
        return Severity.ERROR;
      case FATAL:
        return Severity.FATAL;
      case OFF:
        return Severity.UNDEFINED_SEVERITY_NUMBER;
    }
    return Severity.UNDEFINED_SEVERITY_NUMBER;
  }

  private LogEventMapper() {}
}
