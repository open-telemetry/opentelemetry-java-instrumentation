/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_16.internal;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.appender.internal.LogBuilder;
import io.opentelemetry.instrumentation.api.appender.internal.Severity;
import io.opentelemetry.instrumentation.api.cache.Cache;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.message.MapMessage;
import org.apache.logging.log4j.message.Message;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class LogEventMapper<T> {

  private static final String SPECIAL_MAP_MESSAGE_ATTRIBUTE = "message";

  private static final Cache<String, AttributeKey<String>> contextDataAttributeKeyCache =
      Cache.bounded(100);
  private static final Cache<String, AttributeKey<String>> mapMessageAttributeKeyCache =
      Cache.bounded(100);

  private final boolean captureMapMessageAttributes;

  private final List<String> captureContextDataAttributes;

  // cached as an optimization
  private final boolean captureAllContextDataAttributes;

  private final ContextDataAccessor<T> contextDataAccessor;

  public LogEventMapper(ContextDataAccessor<T> contextDataAccessor) {
    this(
        contextDataAccessor,
        Config.get()
            .getBoolean(
                "otel.instrumentation.log4j-appender.experimental.capture-map-message-attributes",
                false),
        Config.get()
            .getList(
                "otel.instrumentation.log4j-appender.experimental.capture-context-data-attributes",
                emptyList()));
  }

  // visible for testing
  LogEventMapper(
      ContextDataAccessor<T> contextDataAccessor,
      boolean captureMapMessageAttributes,
      List<String> captureContextDataAttributes) {

    this.contextDataAccessor = contextDataAccessor;
    this.captureMapMessageAttributes = captureMapMessageAttributes;
    this.captureContextDataAttributes = captureContextDataAttributes;
    this.captureAllContextDataAttributes =
        captureContextDataAttributes.size() == 1 && captureContextDataAttributes.get(0).equals("*");
  }

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
  public void mapLogEvent(
      LogBuilder builder,
      Message message,
      Level level,
      @Nullable Throwable throwable,
      @Nullable Instant timestamp,
      T contextData) {

    AttributesBuilder attributes = Attributes.builder();

    captureMessage(builder, attributes, message);

    if (level != null) {
      builder.setSeverity(levelToSeverity(level));
      builder.setSeverityText(level.name());
    }

    if (throwable != null) {
      setThrowable(attributes, throwable);
    }

    captureContextDataAttributes(attributes, contextData);

    builder.setAttributes(attributes.build());

    builder.setContext(Context.current());

    if (timestamp != null) {
      builder.setEpoch(
          TimeUnit.MILLISECONDS.toNanos(timestamp.getEpochMillisecond())
              + timestamp.getNanoOfMillisecond(),
          TimeUnit.NANOSECONDS);
    }
  }

  // visible for testing
  void captureMessage(LogBuilder builder, AttributesBuilder attributes, Message message) {
    if (message == null) {
      return;
    }
    if (!(message instanceof MapMessage)) {
      builder.setBody(message.getFormattedMessage());
      return;
    }

    MapMessage<?, ?> mapMessage = (MapMessage<?, ?>) message;

    String body = mapMessage.getFormat();
    boolean checkSpecialMapMessageAttribute = (body == null || body.isEmpty());
    if (checkSpecialMapMessageAttribute) {
      body = mapMessage.get(SPECIAL_MAP_MESSAGE_ATTRIBUTE);
    }

    if (body != null && !body.isEmpty()) {
      builder.setBody(body);
    }

    if (captureMapMessageAttributes) {
      mapMessage.forEach(
          (key, value) -> {
            if (value != null
                && (!checkSpecialMapMessageAttribute
                    || !key.equals(SPECIAL_MAP_MESSAGE_ATTRIBUTE))) {
              attributes.put(
                  mapMessageAttributeKeyCache.computeIfAbsent(key, AttributeKey::stringKey),
                  value.toString());
            }
          });
    }
  }

  // visible for testing
  void captureContextDataAttributes(AttributesBuilder attributes, T contextData) {

    if (captureAllContextDataAttributes) {
      contextDataAccessor.forEach(
          contextData,
          (key, value) -> {
            if (value != null) {
              attributes.put(getContextDataAttributeKey(key), value.toString());
            }
          });
      return;
    }

    for (String key : captureContextDataAttributes) {
      Object value = contextDataAccessor.getValue(contextData, key);
      if (value != null) {
        attributes.put(getContextDataAttributeKey(key), value.toString());
      }
    }
  }

  public static AttributeKey<String> getContextDataAttributeKey(String key) {
    return contextDataAttributeKeyCache.computeIfAbsent(
        key, k -> AttributeKey.stringKey("log4j.context_data." + k));
  }

  private static void setThrowable(AttributesBuilder attributes, Throwable throwable) {
    // TODO (trask) extract method for recording exception into
    // instrumentation-appender-api-internal
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
}
