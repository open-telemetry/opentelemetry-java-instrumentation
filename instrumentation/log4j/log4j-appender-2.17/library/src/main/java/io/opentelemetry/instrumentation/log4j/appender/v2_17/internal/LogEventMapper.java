/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FILE_PATH;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FUNCTION_NAME;
import static io.opentelemetry.semconv.CodeAttributes.CODE_LINE_NUMBER;
import static io.opentelemetry.semconv.OtelAttributes.OTEL_EVENT_NAME;
import static java.util.stream.Collectors.toList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.message.MapMessage;
import org.apache.logging.log4j.message.Message;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class LogEventMapper<T> {

  // copied from ThreadIncubatingAttributes
  private static final AttributeKey<Long> THREAD_ID = AttributeKey.longKey("thread.id");
  private static final AttributeKey<String> THREAD_NAME = stringKey("thread.name");
  private static final String SPECIAL_MAP_MESSAGE_ATTRIBUTE = "message";

  private static final Cache<String, AttributeKey<String>> contextDataAttributeKeyCache =
      Cache.bounded(100);
  private static final Cache<String, AttributeKey<Boolean>> contextDataBooleanAttributeKeyCache =
      Cache.bounded(100);
  private static final Cache<String, AttributeKey<Long>> contextDataLongAttributeKeyCache =
      Cache.bounded(100);
  private static final Cache<String, AttributeKey<Double>> contextDataDoubleAttributeKeyCache =
      Cache.bounded(100);
  private final Cache<String, AttributeKey<String>> mapMessageAttributeKeyCache =
      Cache.bounded(100);

  private static final AttributeKey<String> LOG_MARKER = stringKey("log4j.marker");
  private static final AttributeKey<String> LOG_BODY_TEMPLATE = stringKey("log.body.template");
  private static final AttributeKey<List<String>> LOG_BODY_PARAMETERS =
      AttributeKey.stringArrayKey("log.body.parameters");

  private final ContextDataAccessor<T> contextDataAccessor;

  private final boolean captureExperimentalAttributes;
  private final boolean captureCodeAttributes;
  @Nullable private final Predicate<String> mapMessageAttributes;
  private final boolean captureMarkerAttribute;
  private final boolean captureTemplate;
  private final boolean captureArguments;
  @Nullable private final Predicate<String> contextDataAttributes;

  @SuppressWarnings("TooManyParameters")
  public LogEventMapper(
      ContextDataAccessor<T> contextDataAccessor,
      boolean captureExperimentalAttributes,
      boolean captureCodeAttributes,
      @Nullable Predicate<String> mapMessageAttributes,
      boolean captureMarkerAttribute,
      boolean captureTemplate,
      boolean captureArguments,
      @Nullable Predicate<String> contextDataAttributes) {

    this.contextDataAccessor = contextDataAccessor;
    this.captureCodeAttributes = captureCodeAttributes;
    this.captureExperimentalAttributes = captureExperimentalAttributes;
    this.mapMessageAttributes = mapMessageAttributes;
    this.captureMarkerAttribute = captureMarkerAttribute;
    this.captureTemplate = captureTemplate;
    this.captureArguments = captureArguments;
    this.contextDataAttributes = contextDataAttributes;
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
      @Nullable String threadName,
      long threadId,
      Supplier<StackTraceElement> sourceSupplier,
      Context context) {

    // Event name priority (last writer wins): MapMessage > context data.
    // Sources are called in ascending priority order.
    captureContextDataAttributes(builder, contextData);

    captureMessage(builder, message);

    Object[] parameters = message == null ? null : message.getParameters();
    if (parameters != null && parameters.length > 0) {
      if (captureTemplate) {
        builder.setAttribute(LOG_BODY_TEMPLATE, message.getFormat());
      }
      if (captureArguments) {
        builder.setAttribute(
            LOG_BODY_PARAMETERS, Arrays.stream(parameters).map(String::valueOf).collect(toList()));
      }
    }

    if (captureMarkerAttribute) {
      if (marker != null) {
        String markerName = marker.getName();
        builder.setAttribute(LOG_MARKER, markerName);
      }
    }

    if (level != null) {
      builder.setSeverity(levelToSeverity(level));
      builder.setSeverityText(level.name());
    }

    if (throwable != null) {
      builder.setException(throwable);
    }

    if (captureExperimentalAttributes) {
      builder.setAttribute(THREAD_NAME, threadName);
      builder.setAttribute(THREAD_ID, threadId);
    }

    if (captureCodeAttributes) {
      StackTraceElement source = sourceSupplier.get();
      if (source != null) {
        String fileName = source.getFileName();
        builder.setAttribute(CODE_FILE_PATH, fileName);
        builder.setAttribute(
            CODE_FUNCTION_NAME, source.getClassName() + "." + source.getMethodName());

        int lineNumber = source.getLineNumber();
        if (lineNumber > 0) {
          builder.setAttribute(CODE_LINE_NUMBER, (long) lineNumber);
        }
      }
    }

    builder.setContext(context);
  }

  // visible for testing
  void captureMessage(LogRecordBuilder builder, Message message) {
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

    String eventName = mapMessage.get(OTEL_EVENT_NAME.getKey());
    if (eventName != null) {
      builder.setEventName(eventName);
    }

    if (mapMessageAttributes != null) {
      // TODO (trask) this could be optimized in 2.9 and later by calling MapMessage.forEach()
      mapMessage
          .getData()
          .forEach(
              (key, value) -> {
                if (value != null
                    && !key.isEmpty()
                    && !key.equals(OTEL_EVENT_NAME.getKey())
                    && (!checkSpecialMapMessageAttribute
                        || !key.equals(SPECIAL_MAP_MESSAGE_ATTRIBUTE))
                    && mapMessageAttributes.test(key)) {
                  builder.setAttribute(getMapMessageAttributeKey(key), value.toString());
                }
              });
    }
  }

  // visible for testing
  void captureContextDataAttributes(LogRecordBuilder builder, T contextData) {

    String otelEventName = contextDataAccessor.getValue(contextData, OTEL_EVENT_NAME.getKey());
    if (otelEventName != null) {
      builder.setEventName(otelEventName);
    }

    if (contextDataAttributes == null) {
      return;
    }

    contextDataAccessor.forEach(
        contextData,
        (key, value) -> {
          // Log4j context data keys may be null
          if (key != null
              && !OTEL_EVENT_NAME.getKey().equals(key)
              && contextDataAttributes.test(key)) {
            captureContextDataAttribute(builder, key, value);
          }
        });
  }

  private static void captureContextDataAttribute(
      LogRecordBuilder builder, String key, @Nullable Object value) {
    if (value == null) {
      return;
    }
    if (value instanceof String) {
      builder.setAttribute(getContextDataAttributeKey(key), (String) value);
    } else if (value instanceof Boolean) {
      builder.setAttribute(
          contextDataBooleanAttributeKeyCache.computeIfAbsent(key, AttributeKey::booleanKey),
          (Boolean) value);
    } else if (isIntegral(value)) {
      builder.setAttribute(
          contextDataLongAttributeKeyCache.computeIfAbsent(key, AttributeKey::longKey),
          ((Number) value).longValue());
    } else if (isFloatingPoint(value)) {
      builder.setAttribute(
          contextDataDoubleAttributeKeyCache.computeIfAbsent(key, AttributeKey::doubleKey),
          ((Number) value).doubleValue());
    } else if (isContainer(value)) {
      // AttributesBuilder converts the value to a simple array attribute when all converted
      // elements have the same simple type
      builder.setAllAttributes(
          Attributes.builder()
              .put(AttributeKey.valueKey(key), toValue(value, newIdentitySet()))
              .build());
    } else {
      builder.setAttribute(getContextDataAttributeKey(key), value.toString());
    }
  }

  // Converts container elements the same way as context data values. Map keys are converted with
  // toString(), null keys are skipped, and colliding keys keep the last value. A container that
  // is already being converted higher up becomes an empty value instead of being converted again,
  // so that cyclic references terminate.
  private static Value<?> toValue(@Nullable Object value, Set<Object> ancestors) {
    if (value == null) {
      return Value.empty();
    }
    if (value instanceof String) {
      return Value.of((String) value);
    }
    if (value instanceof Boolean) {
      return Value.of((Boolean) value);
    }
    if (isIntegral(value)) {
      return Value.of(((Number) value).longValue());
    }
    if (isFloatingPoint(value)) {
      return Value.of(((Number) value).doubleValue());
    }
    if (!isContainer(value)) {
      return Value.of(value.toString());
    }
    if (!ancestors.add(value)) {
      return Value.empty();
    }
    Value<?> converted = toContainerValue(value, ancestors);
    ancestors.remove(value);
    return converted;
  }

  private static Value<?> toContainerValue(Object value, Set<Object> ancestors) {
    if (value instanceof Collection) {
      Collection<?> collection = (Collection<?>) value;
      List<Value<?>> values = new ArrayList<>(collection.size());
      for (Object element : collection) {
        values.add(toValue(element, ancestors));
      }
      return Value.of(values);
    }
    if (value instanceof Map) {
      Map<String, Value<?>> values = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        Object entryKey = entry.getKey();
        if (entryKey != null) {
          values.put(entryKey.toString(), toValue(entry.getValue(), ancestors));
        }
      }
      return Value.of(values);
    }
    int length = Array.getLength(value);
    List<Value<?>> values = new ArrayList<>(length);
    for (int i = 0; i < length; i++) {
      values.add(toValue(Array.get(value, i), ancestors));
    }
    return Value.of(values);
  }

  private static boolean isIntegral(Object value) {
    return value instanceof Byte
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long;
  }

  private static boolean isFloatingPoint(Object value) {
    return value instanceof Float || value instanceof Double;
  }

  private static boolean isContainer(Object value) {
    return value instanceof Collection || value instanceof Map || value.getClass().isArray();
  }

  // identity based, because equals() and hashCode() of a container that contains itself overflow
  private static Set<Object> newIdentitySet() {
    return Collections.newSetFromMap(new IdentityHashMap<>());
  }

  public static AttributeKey<String> getContextDataAttributeKey(String key) {
    return contextDataAttributeKeyCache.computeIfAbsent(key, AttributeKey::stringKey);
  }

  public AttributeKey<String> getMapMessageAttributeKey(String key) {
    return mapMessageAttributeKeyCache.computeIfAbsent(key, AttributeKey::stringKey);
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
