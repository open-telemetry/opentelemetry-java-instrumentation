/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_16.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.appender.LogBuilder;
import io.opentelemetry.instrumentation.api.appender.Severity;
import io.opentelemetry.instrumentation.api.cache.Cache;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.BiConsumer;

public final class LogEventMapper {

  private static final boolean CAPTURE_EXPERIMENTAL_LOG_ATTRIBUTES =
      Config.get()
          .getBoolean("otel.instrumentation.log4j-appender.experimental-log-attributes", false);

  private static final Cache<String, AttributeKey<String>> mdcAttributeKeys = Cache.bounded(100);

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
   * </ul>
   */
  public static <T> void mapLogEvent(
      LogBuilder builder,
      Message message,
      Level level,
      @Nullable Throwable throwable,
      @Nullable Instant timestamp,
      T contextData,
      // passing this is just an optimization to avoid creating AttributesBuilder when not necessary
      boolean contextDataIsEmpty,
      BiConsumer<AttributesBuilder, T> contextDataMapper) {

    if (message != null) {
      builder.setBody(message.getFormattedMessage());
    }

    if (level != null) {
      builder.setSeverity(levelToSeverity(level));
      builder.setSeverityText(level.name());
    }

    // conditional is an optimization to avoid creating AttributesBuilder when not necessary
    if (throwable != null || (CAPTURE_EXPERIMENTAL_LOG_ATTRIBUTES && !contextDataIsEmpty)) {
      AttributesBuilder attributes = Attributes.builder();
      if (throwable != null) {
        setThrowable(attributes, throwable);
      }
      if (CAPTURE_EXPERIMENTAL_LOG_ATTRIBUTES && !contextDataIsEmpty) {
        contextDataMapper.accept(attributes, contextData);
      }
      builder.setAttributes(attributes.build());
    }

    builder.setContext(Context.current());

    if (timestamp != null) {
      builder.setEpoch(
          TimeUnit.MILLISECONDS.toNanos(timestamp.getEpochMillisecond())
              + timestamp.getNanoOfMillisecond(),
          TimeUnit.NANOSECONDS);
    }
  }

  public static AttributeKey<String> getMdcAttributeKey(String key) {
    return mdcAttributeKeys.computeIfAbsent(
        key, k -> AttributeKey.stringKey("log4j.context_data." + k));
  }

  private static void setThrowable(AttributesBuilder attributes, Throwable throwable) {
    // TODO (trask) extract method for recording exception into instrumentation-api-appender
    attributes.put(SemanticAttributes.EXCEPTION_TYPE, throwable.getClass().getName());
    attributes.put(SemanticAttributes.EXCEPTION_MESSAGE, throwable.getMessage());
    StringWriter writer = new StringWriter();
    throwable.printStackTrace(new PrintWriter(writer));
    attributes.put(SemanticAttributes.EXCEPTION_STACKTRACE, writer.toString());
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
