/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.incubator.common.ExtendedAttributeKey;
import io.opentelemetry.api.incubator.common.ExtendedAttributes;
import io.opentelemetry.api.incubator.common.ExtendedAttributesBuilder;
import io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.semconv.ExceptionAttributes;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.MutableLogEvent;
import org.apache.logging.log4j.message.MapMessage;
import org.apache.logging.log4j.message.Message;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class LogEventMapper<T> {

  // copied from CodeIncubatingAttributes
  private static final AttributeKey<String> CODE_FILEPATH = AttributeKey.stringKey("code.filepath");
  private static final AttributeKey<String> CODE_FUNCTION = AttributeKey.stringKey("code.function");
  private static final AttributeKey<Long> CODE_LINENO = AttributeKey.longKey("code.lineno");
  private static final AttributeKey<String> CODE_NAMESPACE =
      AttributeKey.stringKey("code.namespace");
  // copied from ThreadIncubatingAttributes
  private static final AttributeKey<Long> THREAD_ID = AttributeKey.longKey("thread.id");
  private static final AttributeKey<String> THREAD_NAME = AttributeKey.stringKey("thread.name");

  private static final String SPECIAL_MAP_MESSAGE_ATTRIBUTE = "message";

  private static final Cache<String, AttributeKey<String>> contextDataAttributeKeyCache =
      Cache.bounded(100);
  private static final Cache<String, AttributeKey<String>> mapMessageAttributeKeyCache =
      Cache.bounded(100);

  private static final AttributeKey<String> LOG_MARKER = AttributeKey.stringKey("log4j.marker");

  private final ContextDataAccessor<T> contextDataAccessor;

  private final boolean captureExperimentalAttributes;
  private final boolean captureCodeAttributes;
  private final boolean captureMapMessageAttributes;
  private final boolean captureMarkerAttribute;
  private final List<String> captureContextDataAttributes;
  private final boolean captureAllContextDataAttributes;

  public LogEventMapper(
      ContextDataAccessor<T> contextDataAccessor,
      boolean captureExperimentalAttributes,
      boolean captureCodeAttributes,
      boolean captureMapMessageAttributes,
      boolean captureMarkerAttribute,
      List<String> captureContextDataAttributes) {

    this.contextDataAccessor = contextDataAccessor;
    this.captureCodeAttributes = captureCodeAttributes;
    this.captureExperimentalAttributes = captureExperimentalAttributes;
    this.captureMapMessageAttributes = captureMapMessageAttributes;
    this.captureMarkerAttribute = captureMarkerAttribute;
    this.captureContextDataAttributes = captureContextDataAttributes;
    this.captureAllContextDataAttributes =
        captureContextDataAttributes.size() == 1 && captureContextDataAttributes.get(0).equals("*");
  }

  /**
   * Map the {@link LogEvent} data model onto the {@link LogRecordBuilder}. Unmapped fields include:
   *
   * <ul>
   *   <li>Fully qualified class name - {@link LogEvent#getLoggerFqcn()}
   *   <li>Thread priority - {@link LogEvent#getThreadPriority()}
   *   <li>Nested diagnostic context - {@link LogEvent#getContextStack()}
   * </ul>
   */
  @SuppressWarnings("TooManyParameters")
  public void mapLogEvent(
      LogRecordBuilder builder,
      Message message,
      Level level,
      @Nullable Marker marker,
      @Nullable Throwable throwable,
      T contextData,
      String threadName,
      long threadId,
      Supplier<StackTraceElement> sourceSupplier,
      Context context) {

    ExtendedAttributesBuilder attributes = ExtendedAttributes.builder();

    captureMessage(builder, attributes, message);

    if (captureMarkerAttribute) {
      if (marker != null) {
        String markerName = marker.getName();
        attributes.put(LOG_MARKER, markerName);
      }
    }

    if (level != null) {
      builder.setSeverity(levelToSeverity(level));
      builder.setSeverityText(level.name());
    }

    if (throwable != null) {
      setThrowable(attributes, throwable);
    }

    captureContextDataAttributes(attributes, contextData);

    if (captureExperimentalAttributes) {
      attributes.put(THREAD_NAME, threadName);
      attributes.put(THREAD_ID, threadId);
    }

    if (captureCodeAttributes) {
      StackTraceElement source = sourceSupplier.get();
      if (source != null) {
        String fileName = source.getFileName();
        if (fileName != null) {
          attributes.put(CODE_FILEPATH, fileName);
        }
        attributes.put(CODE_NAMESPACE, source.getClassName());
        attributes.put(CODE_FUNCTION, source.getMethodName());
        long lineNumber = source.getLineNumber();
        if (lineNumber > 0) {
          attributes.put(CODE_LINENO, lineNumber);
        }
      }
    }

    if (builder instanceof ExtendedLogRecordBuilder) {
      ((ExtendedLogRecordBuilder) builder).setAllAttributes(attributes.build());
    } else {
      builder.setAllAttributes(attributes.build().asAttributes());
    }
    builder.setContext(context);
  }

  // visible for testing
  void captureMessage(
      LogRecordBuilder builder, ExtendedAttributesBuilder attributes, Message message) {
    if (message == null) {
      return;
    }

    if (message instanceof MutableLogEvent) {
      captureLogEvent(attributes, (MutableLogEvent) message);
      return;
    }

    if (message instanceof MapMessage) {
      captureMapMessage(builder, attributes, (MapMessage<?, ?>) message);
      return;
    }

    builder.setBody(message.getFormattedMessage());
  }

  @SuppressWarnings("unchecked")
  private void captureMapMessage(
      LogRecordBuilder builder, ExtendedAttributesBuilder attributes, MapMessage<?, ?> mapMessage) {
    String body = mapMessage.getFormat();
    boolean checkSpecialMapMessageAttribute = false;
    if (body == null || body.isEmpty()) {
      checkSpecialMapMessageAttribute = true;
      body = mapMessage.get(SPECIAL_MAP_MESSAGE_ATTRIBUTE);
    }
    if (body != null && !body.isEmpty()) {
      builder.setBody(body);
    }

    if (captureMapMessageAttributes) {
      consumeAttributes(mapMessage.getData()::forEach, attributes, checkSpecialMapMessageAttribute);
    }
  }

  @SuppressWarnings("unchecked")
  private void captureLogEvent(ExtendedAttributesBuilder attributes, MutableLogEvent logEvent) {
    if (captureMapMessageAttributes) {
      consumeAttributes(
          biConsumer ->
              logEvent.getContextData().forEach((s, object) -> biConsumer.accept(s, object)),
          attributes,
          false);
    }
  }

  @SuppressWarnings("rawtypes")
  private static void consumeAttributes(
      Consumer<BiConsumer> actionConsumer,
      ExtendedAttributesBuilder attributes,
      boolean checkSpecialMapMessageAttribute) {
    actionConsumer.accept(
        (key, value) -> {
          if (value != null
              && (!checkSpecialMapMessageAttribute || !key.equals(SPECIAL_MAP_MESSAGE_ATTRIBUTE))) {
            consumeEntry(key.toString(), value, attributes);
          }
        });
  }

  @SuppressWarnings({"unchecked"})
  private static void consumeEntry(String key, Object value, ExtendedAttributesBuilder attributes) {
    if (value instanceof String) {
      attributes.put(key, (String) value);
    } else if (value instanceof Boolean) {
      attributes.put(key, (Boolean) value);
    } else if ((value instanceof Long) || (value instanceof Integer)) {
      attributes.put(key, (long) value);
    } else if ((value instanceof Double) || (value instanceof Float)) {
      attributes.put(key, (double) value);
    } else if (value instanceof List) {
      List<?> list = (List<?>) value;
      if (list.isEmpty()) {
        return;
      }

      Object first = list.get(0);
      if (first instanceof String) {
        attributes.put(ExtendedAttributeKey.stringArrayKey(key), (List<String>) value);
      } else if (first instanceof Boolean) {
        attributes.put(ExtendedAttributeKey.booleanArrayKey(key), (List<Boolean>) value);
      } else if ((first instanceof Long) || (first instanceof Integer)) {
        attributes.put(ExtendedAttributeKey.longArrayKey(key), (List<Long>) value);
      } else if ((first instanceof Double) || (first instanceof Float)) {
        attributes.put(ExtendedAttributeKey.doubleArrayKey(key), (List<Double>) value);
      }
    } else if (value instanceof String[]) {
      attributes.put(key, (String[]) value);
    } else if (value instanceof boolean[]) {
      attributes.put(key, (boolean[]) value);
    } else if (value instanceof long[]) {
      attributes.put(key, (long[]) value);
    } else if (value instanceof int[]) {
      attributes.put(key, Arrays.stream((int[]) value).asLongStream().toArray());
    } else if (value instanceof double[]) {
      attributes.put(key, (double[]) value);
    } else if (value instanceof float[]) {
      double[] arr = new double[((float[]) value).length];
      for (int i = 0; i < arr.length; ++i) {
        arr[i] = ((float[]) value)[i];
      }
      attributes.put(key, arr);
    } else if ((value instanceof Map)) {
      ExtendedAttributesBuilder nestedAttribute = ExtendedAttributes.builder();
      consumeAttributes(((Map<?, ?>) value)::forEach, nestedAttribute, false);
      attributes.put(ExtendedAttributeKey.extendedAttributesKey(key), nestedAttribute.build());
    } else {
      throw new IllegalArgumentException("Unrecognized value type: " + value.getClass());
    }
  }

  // visible for testing
  void captureContextDataAttributes(ExtendedAttributesBuilder attributes, T contextData) {
    if (captureAllContextDataAttributes) {
      contextDataAccessor.forEach(
          contextData,
          (key, value) -> {
            if (value != null) {
              attributes.put(getContextDataAttributeKey(key), value);
            }
          });
      return;
    }

    for (String key : captureContextDataAttributes) {
      String value = contextDataAccessor.getValue(contextData, key);
      if (value != null) {
        attributes.put(getContextDataAttributeKey(key), value);
      }
    }
  }

  public static AttributeKey<String> getContextDataAttributeKey(String key) {
    return contextDataAttributeKeyCache.computeIfAbsent(key, AttributeKey::stringKey);
  }

  public static AttributeKey<String> getMapMessageAttributeKey(String key) {
    return mapMessageAttributeKeyCache.computeIfAbsent(
        key, k -> AttributeKey.stringKey("log4j.map_message." + k));
  }

  private static void setThrowable(ExtendedAttributesBuilder attributes, Throwable throwable) {
    // TODO (trask) extract method for recording exception into
    // io.opentelemetry:opentelemetry-api
    attributes.put(ExceptionAttributes.EXCEPTION_TYPE, throwable.getClass().getName());
    attributes.put(ExceptionAttributes.EXCEPTION_MESSAGE, throwable.getMessage());
    StringWriter writer = new StringWriter();
    throwable.printStackTrace(new PrintWriter(writer));
    attributes.put(ExceptionAttributes.EXCEPTION_STACKTRACE, writer.toString());
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
