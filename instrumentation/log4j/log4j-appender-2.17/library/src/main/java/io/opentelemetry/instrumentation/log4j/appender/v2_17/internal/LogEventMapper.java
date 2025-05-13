/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.incubator.common.ExtendedAttributeKey;
import io.opentelemetry.api.incubator.common.ExtendedAttributeType;
import io.opentelemetry.api.incubator.common.ExtendedAttributes;
import io.opentelemetry.api.incubator.common.ExtendedAttributesBuilder;
import io.opentelemetry.api.incubator.internal.InternalExtendedAttributeKeyImpl;
import io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.semconv.ExceptionAttributes;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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

  private static final Cache<String, ExtendedAttributeKey<?>> contextDataAttributeKeyCache =
      Cache.bounded(100);
  private static final Cache<String, ExtendedAttributeKey<?>> mapMessageAttributeKeyCache =
      Cache.bounded(100);

  private static final AttributeKey<String> LOG_MARKER = AttributeKey.stringKey("log4j.marker");

  private final ContextDataAccessor<T, Object> contextDataAccessor;

  private final boolean captureExperimentalAttributes;
  private final boolean captureCodeAttributes;
  private final boolean captureMapMessageAttributes;
  private final boolean captureMarkerAttribute;
  private final List<String> captureContextDataAttributes;
  private final boolean captureAllContextDataAttributes;

  public LogEventMapper(
      ContextDataAccessor<T, Object> contextDataAccessor,
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
      // TODO (trask) this could be optimized in 2.9 and later by calling MapMessage.forEach()
      consumeAttributes(
          mapMessage.getData()::forEach,
          attributes,
          checkSpecialMapMessageAttribute,
          LogEventMapper::getMapMessageAttributeKey);
    }
  }

  private static void consumeAttributes(
      // Consumes an action on an entry, like map::forEach
      Consumer<BiConsumer<String, Object>> entryActionConsumer,
      ExtendedAttributesBuilder attributes,
      boolean checkSpecialMapMessageAttribute,
      BiFunction<String, ExtendedAttributeType, ExtendedAttributeKey<?>> keyProvider) {
    entryActionConsumer.accept(
        (key, value) -> {
          if (value != null
              && (!checkSpecialMapMessageAttribute || !key.equals(SPECIAL_MAP_MESSAGE_ATTRIBUTE))) {
            consumeEntry(key, value, attributes, keyProvider);
          }
        });
  }

  @SuppressWarnings({"unchecked"})
  private static void consumeEntry(
      String key,
      Object value,
      ExtendedAttributesBuilder attributes,
      BiFunction<String, ExtendedAttributeType, ExtendedAttributeKey<?>> keyProvider) {
    if (value instanceof String) {
      attributes.put(
          (ExtendedAttributeKey<String>) keyProvider.apply(key, ExtendedAttributeType.STRING),
          (String) value);
    } else if (value instanceof Boolean) {
      attributes.put(
          (ExtendedAttributeKey<Boolean>) keyProvider.apply(key, ExtendedAttributeType.BOOLEAN),
          (Boolean) value);
    } else if (value instanceof Byte
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long) {
      attributes.put(
          (ExtendedAttributeKey<Long>) keyProvider.apply(key, ExtendedAttributeType.LONG),
          ((Number) value).longValue());
    } else if (value instanceof Float || value instanceof Double) {
      attributes.put(
          (ExtendedAttributeKey<Double>) keyProvider.apply(key, ExtendedAttributeType.DOUBLE),
          ((Number) value).doubleValue());
    } else if (value instanceof List) {
      List<?> list = (List<?>) value;
      if (list.isEmpty()) {
        return;
      }

      Object first = list.get(0);
      if (first instanceof String) {
        attributes.put(
            (ExtendedAttributeKey<List<String>>)
                keyProvider.apply(key, ExtendedAttributeType.STRING_ARRAY),
            (List<String>) value);
      } else if (first instanceof Boolean) {
        attributes.put(
            (ExtendedAttributeKey<List<Boolean>>)
                keyProvider.apply(key, ExtendedAttributeType.BOOLEAN_ARRAY),
            (List<Boolean>) value);
      } else if (first instanceof Integer) {
        attributes.put(
            (ExtendedAttributeKey<List<Long>>)
                keyProvider.apply(key, ExtendedAttributeType.LONG_ARRAY),
            ((List<Integer>) value).stream().map(Integer::longValue).collect(Collectors.toList()));
      } else if (first instanceof Long) {
        attributes.put(
            (ExtendedAttributeKey<List<Long>>)
                keyProvider.apply(key, ExtendedAttributeType.LONG_ARRAY),
            (List<Long>) value);
      } else if (first instanceof Float) {
        attributes.put(
            (ExtendedAttributeKey<List<Double>>)
                keyProvider.apply(key, ExtendedAttributeType.DOUBLE_ARRAY),
            ((List<Float>) value).stream().map(Float::doubleValue).collect(Collectors.toList()));
      } else if (first instanceof Double) {
        attributes.put(
            (ExtendedAttributeKey<List<Double>>)
                keyProvider.apply(key, ExtendedAttributeType.DOUBLE_ARRAY),
            (List<Double>) value);
      }
    } else if (value instanceof String[]) {
      attributes.put(
          (ExtendedAttributeKey<List<String>>)
              keyProvider.apply(key, ExtendedAttributeType.STRING_ARRAY),
          Arrays.asList((String[]) value));
    } else if (value instanceof boolean[]) {
      boolean[] arr = (boolean[]) value;
      List<Boolean> list = new ArrayList<>(arr.length);
      for (boolean f : arr) {
        list.add(f);
      }
      attributes.put(
          (ExtendedAttributeKey<List<Boolean>>)
              keyProvider.apply(key, ExtendedAttributeType.BOOLEAN_ARRAY),
          list);
    } else if (value instanceof long[]) {
      List<Long> list = Arrays.stream((long[]) value).boxed().collect(Collectors.toList());
      attributes.put(
          (ExtendedAttributeKey<List<Long>>)
              keyProvider.apply(key, ExtendedAttributeType.LONG_ARRAY),
          list);
    } else if (value instanceof int[]) {
      List<Long> list =
          Arrays.stream((int[]) value).mapToLong(i -> i).boxed().collect(Collectors.toList());
      attributes.put(
          (ExtendedAttributeKey<List<Long>>)
              keyProvider.apply(key, ExtendedAttributeType.LONG_ARRAY),
          list);
    } else if (value instanceof double[]) {
      List<Double> list = Arrays.stream((double[]) value).boxed().collect(Collectors.toList());
      attributes.put(
          (ExtendedAttributeKey<List<Double>>)
              keyProvider.apply(key, ExtendedAttributeType.DOUBLE_ARRAY),
          list);
    } else if (value instanceof float[]) {
      float[] arr = (float[]) value;
      List<Double> list = new ArrayList<>(arr.length);
      for (float f : arr) {
        list.add((double) f);
      }
      attributes.put(
          (ExtendedAttributeKey<List<Double>>)
              keyProvider.apply(key, ExtendedAttributeType.DOUBLE_ARRAY),
          list);
    } else if ((value instanceof Map)) {
      ExtendedAttributesBuilder nestedAttribute = ExtendedAttributes.builder();
      Map<?, ?> nestedMap = (Map<?, ?>) value;
      consumeAttributes(
          consumer -> nestedMap.forEach((k, v) -> consumer.accept(k.toString(), v)),
          nestedAttribute,
          false,
          keyProvider);
      attributes.put(
          (ExtendedAttributeKey<ExtendedAttributes>)
              keyProvider.apply(key, ExtendedAttributeType.EXTENDED_ATTRIBUTES),
          nestedAttribute.build());
    } else {
      throw new IllegalArgumentException("Unrecognized value type: " + value.getClass());
    }
  }

  // visible for testing
  void captureContextDataAttributes(ExtendedAttributesBuilder attributes, T contextData) {
    if (captureAllContextDataAttributes) {
      consumeAttributes(
          entryConsumer -> contextDataAccessor.forEach(contextData, entryConsumer),
          attributes,
          false,
          LogEventMapper::getContextDataAttributeKey);
      return;
    }

    for (String key : captureContextDataAttributes) {
      Object value = contextDataAccessor.getValue(contextData, key);
      if (value != null) {
        consumeEntry(key, value, attributes, LogEventMapper::getContextDataAttributeKey);
      }
    }
  }

  private static <T> ExtendedAttributeKey<T> getContextDataAttributeKey(
      String key, ExtendedAttributeType type) {
    return getAttributeKey(key, type, contextDataAttributeKeyCache);
  }

  private static <T> ExtendedAttributeKey<T> getMapMessageAttributeKey(
      String key, ExtendedAttributeType type) {
    return getAttributeKey("log4j.map_message." + key, type, mapMessageAttributeKeyCache);
  }

  @SuppressWarnings("unchecked")
  private static <T> ExtendedAttributeKey<T> getAttributeKey(
      String key, ExtendedAttributeType type, Cache<String, ExtendedAttributeKey<?>> cache) {
    ExtendedAttributeKey<?> output =
        cache.computeIfAbsent(key, k -> InternalExtendedAttributeKeyImpl.create(key, type));
    if (output.getType() != type) {
      output = InternalExtendedAttributeKeyImpl.create(key, type);
      cache.put(key, output);
    }
    return (ExtendedAttributeKey<T>) output;
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
