/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.v2_13_2;

import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.SPAN_ID;
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.TRACE_FLAGS;
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.TRACE_ID;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.LogBuilder;
import io.opentelemetry.sdk.logs.data.Severity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

final class LogEventMapper {

  // Visible for testing
  static final AttributeKey<String> ATTR_LOGGER_NAME = AttributeKey.stringKey("logger.name");
  static final AttributeKey<String> ATTR_THREAD_NAME = AttributeKey.stringKey("thread.name");
  static final AttributeKey<Long> ATTR_THREAD_ID = AttributeKey.longKey("thread.id");
  static final AttributeKey<Long> ATTR_THREAD_PRIORITY = AttributeKey.longKey("thread.priority");
  static final AttributeKey<String> ATTR_THROWABLE_NAME = AttributeKey.stringKey("throwable.name");
  static final AttributeKey<String> ATTR_THROWABLE_MESSAGE =
      AttributeKey.stringKey("throwable.message");
  static final AttributeKey<List<String>> ATTR_NDC = AttributeKey.stringArrayKey("ndc");
  static final AttributeKey<String> ATTR_FQCN = AttributeKey.stringKey("fqcn");
  static final AttributeKey<String> ATTR_MARKER = AttributeKey.stringKey("marker");

  static void mapLogEvent(LogBuilder builder, LogEvent logEvent) {
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

    // logger
    attributes.put(ATTR_LOGGER_NAME, logEvent.getLoggerName());

    // fully qualified class name
    attributes.put(ATTR_FQCN, logEvent.getLoggerFqcn());

    // thread
    attributes.put(ATTR_THREAD_NAME, logEvent.getThreadName());
    attributes.put(ATTR_THREAD_ID, logEvent.getThreadId());
    attributes.put(ATTR_THREAD_PRIORITY, logEvent.getThreadPriority());

    // throwable
    Throwable throwable = logEvent.getThrown();
    if (throwable != null) {
      attributes.put(ATTR_THROWABLE_NAME, throwable.getClass().getName());
      attributes.put(ATTR_THROWABLE_MESSAGE, throwable.getMessage());
    }

    // marker
    Marker marker = logEvent.getMarker();
    if (marker != null) {
      attributes.put(ATTR_MARKER, marker.getName());
    }

    // nested diagnostic context
    List<String> contextStackList = logEvent.getContextStack().asList();
    if (contextStackList.size() > 0) {
      attributes.put(ATTR_NDC, contextStackList);
    }

    // mapped diagnostic context (included span context)
    ReadOnlyStringMap contextData = logEvent.getContextData();
    if (contextData != null) {
      Map<String, String> contextMap = contextData.toMap();
      if (contextMap != null) {
        // Remove context fields placed by OpenTelemetryContextDataProvider to avoid duplicating
        // trace context fields in attributes
        contextMap.remove(TRACE_ID);
        contextMap.remove(SPAN_ID);
        contextMap.remove(TRACE_FLAGS);
        builder.setContext(Context.current());

        contextMap.forEach(attributes::put);
      }
    }

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
    throw new IllegalStateException("Unrecognized level " + level.name());
  }

  private LogEventMapper() {}
}
