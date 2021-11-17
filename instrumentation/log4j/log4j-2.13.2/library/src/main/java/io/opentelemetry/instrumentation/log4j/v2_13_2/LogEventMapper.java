/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.v2_13_2;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.LogBuilder;
import io.opentelemetry.sdk.logs.data.Severity;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.message.Message;

final class LogEventMapper {

  // Visible for testing
  static final AttributeKey<String> ATTR_THROWABLE_MESSAGE =
      AttributeKey.stringKey("throwable.message");

  /**
   * Map the {@link LogEvent} data model onto the {@link LogBuilder}. Unmapped fields include:
   *
   * <ul>
   *   <li>Fully qualified class name - {@link LogEvent#getLoggerFqcn()}
   *   <li>Thread name - {@link LogEvent#getThreadName()}
   *   <li>Thread id - {@link LogEvent#getThreadId()}
   *   <li>Thread priority - {@link LogEvent#getThreadPriority()}
   *   <li>Thread priority - {@link LogEvent#getThreadPriority()}
   *   <li>Thrown details (stack trace, class name) - {@link LogEvent#getThrown()}
   *   <li>Marker - {@link LogEvent#getMarker()}
   *   <li>Nested diagnostic context - {@link LogEvent#getContextStack()}
   *   <li>Mapped diagnostic context - {@link LogEvent#getContextData()}
   * </ul>
   */
  static void mapLogEvent(LogBuilder builder, LogEvent logEvent) {
    // TODO: map the LogEvent more completely when semantic conventions allow it
    AttributesBuilder attributes = Attributes.builder();

    // message
    Message message = logEvent.getMessage();
    if (message != null) {
      builder.setBody(message.getFormattedMessage());
    }

    // time
    Instant instant = logEvent.getInstant();
    if (instant != null) {
      builder.setEpoch(
          TimeUnit.MILLISECONDS.toNanos(instant.getEpochMillisecond())
              + instant.getNanoOfMillisecond(),
          TimeUnit.NANOSECONDS);
    }

    // level
    Level level = logEvent.getLevel();
    if (level != null) {
      builder.setSeverity(levelToSeverity(level));
      builder.setSeverityText(logEvent.getLevel().name());
    }

    // throwable
    Throwable throwable = logEvent.getThrown();
    if (throwable != null) {
      attributes.put(ATTR_THROWABLE_MESSAGE, throwable.getMessage());
    }

    // span context
    builder.setContext(Context.current());

    builder.setAttributes(attributes.build());
  }

  private static Severity levelToSeverity(Level level) {
    switch (level.getStandardLevel()) {
      case ALL:
        return Severity.TRACE;
      case TRACE:
        return Severity.TRACE2;
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
