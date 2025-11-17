/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0.internal;

import static io.opentelemetry.semconv.CodeAttributes.CODE_FILE_PATH;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FUNCTION_NAME;
import static io.opentelemetry.semconv.CodeAttributes.CODE_LINE_NUMBER;
import static java.util.Collections.emptyList;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import io.opentelemetry.semconv.ExceptionAttributes;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.logstash.logback.marker.LogstashMarker;
import net.logstash.logback.marker.MapEntriesAppendingMarker;
import net.logstash.logback.marker.SingleFieldAppendingMarker;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class LoggingEventMapper {
  // copied from CodeIncubatingAttributes
  private static final AttributeKey<String> CODE_FILEPATH = AttributeKey.stringKey("code.filepath");
  private static final AttributeKey<String> CODE_NAMESPACE =
      AttributeKey.stringKey("code.namespace");
  private static final AttributeKey<String> CODE_FUNCTION = AttributeKey.stringKey("code.function");
  private static final AttributeKey<Long> CODE_LINENO = AttributeKey.longKey("code.lineno");
  // copied from ThreadIncubatingAttributes
  private static final AttributeKey<Long> THREAD_ID = AttributeKey.longKey("thread.id");
  private static final AttributeKey<String> THREAD_NAME = AttributeKey.stringKey("thread.name");
  // copied from EventIncubatingAttributes
  private static final AttributeKey<String> EVENT_NAME = AttributeKey.stringKey("event.name");

  private static final boolean supportsInstant = supportsInstant();
  private static final boolean supportsKeyValuePairs = supportsKeyValuePairs();
  private static final boolean supportsMultipleMarkers = supportsMultipleMarkers();
  private static final boolean supportsLogstashMarkers = supportsLogstashMarkers();
  private static final boolean supportsLogstashStructuredArguments =
      supportsLogstashStructuredArguments();
  private static final Cache<String, AttributeKey<String>> attributeKeys = Cache.bounded(100);

  private static final AttributeKey<List<String>> LOG_MARKER =
      AttributeKey.stringArrayKey("logback.marker");
  private static final AttributeKey<String> LOG_BODY_TEMPLATE =
      AttributeKey.stringKey("log.body.template");
  private static final AttributeKey<List<String>> LOG_BODY_PARAMETERS =
      AttributeKey.stringArrayKey("log.body.parameters");

  private final boolean captureExperimentalAttributes;
  private final List<String> captureMdcAttributes;
  private final boolean captureAllMdcAttributes;
  private final boolean captureCodeAttributes;
  private final boolean captureMarkerAttribute;
  private final boolean captureKeyValuePairAttributes;
  private final boolean captureLoggerContext;
  private final boolean captureArguments;
  private final boolean captureLogstashMarkerAttributes;
  private final boolean captureLogstashStructuredArguments;
  private final boolean captureEventName;

  private LoggingEventMapper(Builder builder) {
    this.captureExperimentalAttributes = builder.captureExperimentalAttributes;
    this.captureCodeAttributes = builder.captureCodeAttributes;
    this.captureMdcAttributes = builder.captureMdcAttributes;
    this.captureMarkerAttribute = builder.captureMarkerAttribute;
    this.captureKeyValuePairAttributes = builder.captureKeyValuePairAttributes;
    this.captureLoggerContext = builder.captureLoggerContext;
    this.captureArguments = builder.captureArguments;
    this.captureLogstashMarkerAttributes = builder.captureLogstashMarkerAttributes;
    this.captureLogstashStructuredArguments = builder.captureLogstashStructuredArguments;
    this.captureAllMdcAttributes =
        builder.captureMdcAttributes.size() == 1 && builder.captureMdcAttributes.get(0).equals("*");
    this.captureEventName = builder.captureEventName;
  }

  public static Builder builder() {
    return new Builder();
  }

  public void emit(LoggerProvider loggerProvider, ILoggingEvent event, long threadId) {
    String instrumentationName = event.getLoggerName();
    if (instrumentationName == null || instrumentationName.isEmpty()) {
      instrumentationName = "ROOT";
    }
    LogRecordBuilder builder =
        loggerProvider.loggerBuilder(instrumentationName).build().logRecordBuilder();
    mapLoggingEvent(builder, event, threadId);
    builder.emit();
  }

  /** Map the {@link ILoggingEvent} data model onto the {@link LogRecordBuilder}. */
  private void mapLoggingEvent(
      LogRecordBuilder builder, ILoggingEvent loggingEvent, long threadId) {
    // message
    String message = loggingEvent.getFormattedMessage();
    if (message != null) {
      builder.setBody(message);
    }

    // time
    if (supportsInstant && hasInstant(loggingEvent)) {
      setTimestampFromInstant(builder, loggingEvent);
    } else {
      long timestamp = loggingEvent.getTimeStamp();
      builder.setTimestamp(timestamp, TimeUnit.MILLISECONDS);
    }

    // level
    Level level = loggingEvent.getLevel();
    if (level != null) {
      builder.setSeverity(levelToSeverity(level));
      builder.setSeverityText(level.levelStr);
    }

    // throwable
    Object throwableProxy = loggingEvent.getThrowableProxy();
    Throwable throwable = null;
    if (throwableProxy instanceof ThrowableProxy) {
      // there is only one other subclass of ch.qos.logback.classic.spi.IThrowableProxy
      // and it is only used for logging exceptions over the wire
      throwable = ((ThrowableProxy) throwableProxy).getThrowable();
    }
    if (throwable != null) {
      setThrowable(builder, throwable);
    }

    captureMdcAttributes(builder, loggingEvent.getMDCPropertyMap());

    if (captureExperimentalAttributes) {
      builder.setAttribute(THREAD_NAME, loggingEvent.getThreadName());
      if (threadId != -1) {
        builder.setAttribute(THREAD_ID, threadId);
      }
    }

    if (captureCodeAttributes) {
      StackTraceElement[] callerData = loggingEvent.getCallerData();
      if (callerData != null && callerData.length > 0) {
        StackTraceElement firstStackElement = callerData[0];
        String fileName = firstStackElement.getFileName();
        int lineNumber = firstStackElement.getLineNumber();

        if (SemconvStability.isEmitOldCodeSemconv()) {
          if (fileName != null) {
            builder.setAttribute(CODE_FILEPATH, fileName);
          }
          builder.setAttribute(CODE_NAMESPACE, firstStackElement.getClassName());
          builder.setAttribute(CODE_FUNCTION, firstStackElement.getMethodName());
          if (lineNumber > 0) {
            builder.setAttribute(CODE_LINENO, (long) lineNumber);
          }
        }
        if (SemconvStability.isEmitStableCodeSemconv()) {
          if (fileName != null) {
            builder.setAttribute(CODE_FILE_PATH, fileName);
          }
          builder.setAttribute(
              CODE_FUNCTION_NAME,
              firstStackElement.getClassName() + "." + firstStackElement.getMethodName());
          if (lineNumber > 0) {
            builder.setAttribute(CODE_LINE_NUMBER, (long) lineNumber);
          }
        }
      }
    }

    if (captureMarkerAttribute) {
      boolean skipLogstashMarkers = supportsLogstashMarkers && captureLogstashMarkerAttributes;
      captureMarkerAttribute(builder, loggingEvent, skipLogstashMarkers);
    }

    if (supportsKeyValuePairs && captureKeyValuePairAttributes) {
      captureKeyValuePairAttributes(builder, loggingEvent);
    }

    if (supportsLogstashStructuredArguments
        && captureLogstashStructuredArguments
        && loggingEvent.getArgumentArray() != null
        && loggingEvent.getArgumentArray().length > 0) {
      captureLogstashStructuredArguments(builder, loggingEvent.getArgumentArray());
    }

    if (captureLoggerContext) {
      captureLoggerContext(builder, loggingEvent.getLoggerContextVO().getPropertyMap());
    }

    if (captureArguments
        && loggingEvent.getArgumentArray() != null
        && loggingEvent.getArgumentArray().length > 0) {
      captureArguments(builder, loggingEvent.getMessage(), loggingEvent.getArgumentArray());
    }

    if (supportsLogstashMarkers && captureLogstashMarkerAttributes) {
      captureLogstashMarkerAttributes(builder, loggingEvent);
    }
    // span context
    builder.setContext(Context.current());
  }

  // getInstant is available since Logback 1.3
  private static boolean supportsInstant() {
    try {
      ILoggingEvent.class.getMethod("getInstant");
    } catch (NoSuchMethodException e) {
      return false;
    }

    return true;
  }

  @NoMuzzle
  private static boolean hasInstant(ILoggingEvent loggingEvent) {
    return loggingEvent.getInstant() != null;
  }

  @NoMuzzle
  private static void setTimestampFromInstant(
      LogRecordBuilder builder, ILoggingEvent loggingEvent) {
    builder.setTimestamp(loggingEvent.getInstant());
  }

  // visible for testing
  void captureMdcAttributes(LogRecordBuilder builder, Map<String, String> mdcProperties) {
    if (captureAllMdcAttributes) {
      for (Map.Entry<String, String> entry : mdcProperties.entrySet()) {
        setAttributeOrEventName(builder, getAttributeKey(entry.getKey()), entry.getValue());
      }
      return;
    }

    for (String key : captureMdcAttributes) {
      String value = mdcProperties.get(key);
      setAttributeOrEventName(builder, getAttributeKey(key), value);
    }
  }

  void captureArguments(LogRecordBuilder builder, String message, Object[] arguments) {
    builder.setAttribute(LOG_BODY_TEMPLATE, message);
    builder.setAttribute(
        LOG_BODY_PARAMETERS,
        Arrays.stream(arguments).map(String::valueOf).collect(Collectors.toList()));
  }

  private static void setThrowable(LogRecordBuilder builder, Throwable throwable) {
    if (builder instanceof ExtendedLogRecordBuilder) {
      ((ExtendedLogRecordBuilder) builder).setException(throwable);
    } else {
      builder.setAttribute(ExceptionAttributes.EXCEPTION_TYPE, throwable.getClass().getName());
      builder.setAttribute(ExceptionAttributes.EXCEPTION_MESSAGE, throwable.getMessage());
      StringWriter writer = new StringWriter();
      throwable.printStackTrace(new PrintWriter(writer));
      builder.setAttribute(ExceptionAttributes.EXCEPTION_STACKTRACE, writer.toString());
    }
  }

  private static Severity levelToSeverity(Level level) {
    switch (level.levelInt) {
      case Level.ALL_INT:
      case Level.TRACE_INT:
        return Severity.TRACE;
      case Level.DEBUG_INT:
        return Severity.DEBUG;
      case Level.INFO_INT:
        return Severity.INFO;
      case Level.WARN_INT:
        return Severity.WARN;
      case Level.ERROR_INT:
        return Severity.ERROR;
      case Level.OFF_INT:
      default:
        return Severity.UNDEFINED_SEVERITY_NUMBER;
    }
  }

  @NoMuzzle
  private void captureKeyValuePairAttributes(LogRecordBuilder builder, ILoggingEvent loggingEvent) {
    List<KeyValuePair> keyValuePairs = loggingEvent.getKeyValuePairs();
    if (keyValuePairs != null) {
      for (KeyValuePair keyValuePair : keyValuePairs) {
        captureAttribute(builder, this.captureEventName, keyValuePair.key, keyValuePair.value);
      }
    }
  }

  // visible for testing
  static void captureAttribute(
      LogRecordBuilder builder, boolean captureEventName, Object key, Object value) {
    // empty values are not serialized
    if (key != null && value != null) {
      String keyStr = key.toString();
      // preserve type for boolean and numeric values, everything else is converted to String
      if (value instanceof Boolean) {
        builder.setAttribute(keyStr, (Boolean) value);
      } else if (value instanceof Byte
          || value instanceof Integer
          || value instanceof Long
          || value instanceof Short) {
        builder.setAttribute(keyStr, ((Number) value).longValue());
      } else if (value instanceof Double || value instanceof Float) {
        builder.setAttribute(keyStr, ((Number) value).doubleValue());
      } else if (value.getClass().isArray()) {
        if (value instanceof boolean[] || value instanceof Boolean[]) {
          captureArrayValueAttribute(
              builder, AttributeKey.booleanArrayKey(keyStr), value, o -> (Boolean) o);
        } else if (value instanceof byte[]
            || value instanceof Byte[]
            || value instanceof int[]
            || value instanceof Integer[]
            || value instanceof long[]
            || value instanceof Long[]
            || value instanceof short[]
            || value instanceof Short[]) {
          captureArrayValueAttribute(
              builder, AttributeKey.longArrayKey(keyStr), value, o -> ((Number) o).longValue());
        } else if (value instanceof float[]
            || value instanceof Float[]
            || value instanceof double[]
            || value instanceof Double[]) {
          captureArrayValueAttribute(
              builder, AttributeKey.doubleArrayKey(keyStr), value, o -> ((Number) o).doubleValue());
        } else {
          captureArrayValueAttribute(
              builder, AttributeKey.stringArrayKey(keyStr), value, String::valueOf);
        }
      } else if (value instanceof Collection) {
        captureArrayValueAttribute(
            builder,
            AttributeKey.stringArrayKey(keyStr),
            ((Collection<?>) value).toArray(),
            String::valueOf);
      } else {
        setAttributeOrEventName(builder, captureEventName, getAttributeKey(keyStr), value);
      }
    }
  }

  private void setAttributeOrEventName(
      LogRecordBuilder builder, AttributeKey<String> key, Object value) {
    setAttributeOrEventName(builder, this.captureEventName, key, value);
  }

  private static void setAttributeOrEventName(
      LogRecordBuilder builder, boolean captureEventName, AttributeKey<String> key, Object value) {
    if (value != null) {
      if (captureEventName && key.equals(EVENT_NAME)) {
        builder.setEventName(value.toString());
      } else {
        builder.setAttribute(key, value.toString());
      }
    }
  }

  private static <T> void captureArrayValueAttribute(
      LogRecordBuilder builder,
      AttributeKey<List<T>> key,
      Object array,
      Function<Object, T> extractor) {
    List<T> list = new ArrayList<>();
    int length = Array.getLength(array);
    for (int i = 0; i < length; i++) {
      Object value = Array.get(array, i);
      if (value != null) {
        list.add(extractor.apply(value));
      }
    }
    // empty lists are not serialized
    if (!list.isEmpty()) {
      builder.setAttribute(key, list);
    }
  }

  private void captureLoggerContext(
      LogRecordBuilder builder, Map<String, String> loggerContextProperties) {
    for (Map.Entry<String, String> entry : loggerContextProperties.entrySet()) {
      setAttributeOrEventName(builder, getAttributeKey(entry.getKey()), entry.getValue());
    }
  }

  public static AttributeKey<String> getAttributeKey(String key) {
    return attributeKeys.computeIfAbsent(key, AttributeKey::stringKey);
  }

  private static boolean supportsKeyValuePairs() {
    try {
      Class.forName("org.slf4j.event.KeyValuePair");
    } catch (ClassNotFoundException e) {
      return false;
    }
    try {
      ILoggingEvent.class.getMethod("getKeyValuePairs");
    } catch (NoSuchMethodException e) {
      return false;
    }

    return true;
  }

  private static void captureMarkerAttribute(
      LogRecordBuilder builder, ILoggingEvent loggingEvent, boolean skipLogstashMarkers) {
    if (supportsMultipleMarkers && hasMultipleMarkers(loggingEvent)) {
      captureMultipleMarkerAttributes(builder, loggingEvent, skipLogstashMarkers);
    } else {
      captureSingleMarkerAttribute(builder, loggingEvent, skipLogstashMarkers);
    }
  }

  @SuppressWarnings("deprecation") // getMarker is deprecate since 1.3.0
  private static void captureSingleMarkerAttribute(
      LogRecordBuilder builder, ILoggingEvent loggingEvent, boolean skipLogstashMarkers) {
    Marker marker = loggingEvent.getMarker();
    if (marker != null && (!skipLogstashMarkers || !isLogstashMarker(marker))) {
      builder.setAttribute(LOG_MARKER, Collections.singletonList(marker.getName()));
    }
  }

  @NoMuzzle
  private static void captureMultipleMarkerAttributes(
      LogRecordBuilder builder, ILoggingEvent loggingEvent, boolean skipLogstashMarkers) {
    List<String> markerNames = new ArrayList<>(loggingEvent.getMarkerList().size());
    for (Marker marker : loggingEvent.getMarkerList()) {
      if (!skipLogstashMarkers || !isLogstashMarker(marker)) {
        markerNames.add(marker.getName());
      }
    }
    if (!markerNames.isEmpty()) {
      builder.setAttribute(LOG_MARKER, markerNames);
    }
  }

  @NoMuzzle
  private static boolean hasMultipleMarkers(ILoggingEvent loggingEvent) {
    List<Marker> markerList = loggingEvent.getMarkerList();
    return markerList != null && markerList.size() > 1;
  }

  private static boolean supportsMultipleMarkers() {
    try {
      ILoggingEvent.class.getMethod("getMarkerList");
    } catch (NoSuchMethodException e) {
      return false;
    }

    return true;
  }

  private void captureLogstashMarkerAttributes(
      LogRecordBuilder builder, ILoggingEvent loggingEvent) {
    if (supportsMultipleMarkers && hasMultipleMarkers(loggingEvent)) {
      captureMultipleLogstashMarkers(builder, loggingEvent);
    } else {
      captureSingleLogstashMarker(builder, loggingEvent);
    }
  }

  @NoMuzzle
  private static boolean isLogstashMarker(Marker marker) {
    return marker instanceof LogstashMarker;
  }

  @SuppressWarnings("deprecation") // getMarker is deprecate since 1.3.0
  private void captureSingleLogstashMarker(LogRecordBuilder builder, ILoggingEvent loggingEvent) {
    Marker marker = loggingEvent.getMarker();
    if (isLogstashMarker(marker)) {
      captureLogstashMarkerAndReferences(builder, marker);
    }
  }

  @NoMuzzle
  private void captureMultipleLogstashMarkers(
      LogRecordBuilder builder, ILoggingEvent loggingEvent) {
    for (Marker marker : loggingEvent.getMarkerList()) {
      if (isLogstashMarker(marker)) {
        captureLogstashMarkerAndReferences(builder, marker);
      }
    }
  }

  @NoMuzzle
  private void captureLogstashMarkerAndReferences(LogRecordBuilder builder, Marker marker) {
    LogstashMarker logstashMarker = (LogstashMarker) marker;
    captureLogstashMarker(builder, logstashMarker);

    if (logstashMarker.hasReferences()) {
      for (Iterator<Marker> it = logstashMarker.iterator(); it.hasNext(); ) {
        Marker referenceMarker = it.next();
        if (isLogstashMarker(referenceMarker)) {
          captureLogstashMarkerAndReferences(builder, referenceMarker);
        }
      }
    }
  }

  private void captureLogstashMarker(LogRecordBuilder builder, Object logstashMarker) {
    FieldReader fieldReader = LogstashFieldReaderHolder.valueField.get(logstashMarker.getClass());
    if (fieldReader != null) {
      fieldReader.read(builder, logstashMarker, this.captureEventName);
    }
  }

  @NoMuzzle
  private static boolean isSingleFieldAppendingMarker(Class<?> type) {
    return SingleFieldAppendingMarker.class.isAssignableFrom(type);
  }

  @NoMuzzle
  private static boolean isMapEntriesAppendingMarker(Class<?> type) {
    return MapEntriesAppendingMarker.class.isAssignableFrom(type);
  }

  private static FieldReader createFieldReader(Class<?> type) {
    if (isSingleFieldAppendingMarker(type)) {
      // ObjectAppendingMarker.fieldValue since v7.0
      // ObjectAppendingMarker.object since v3.0
      // RawJsonAppendingMarker.rawJson since v3.0
      return createStringReader(findValueField(type, "fieldValue", "object", "rawJson"));
    } else if (isMapEntriesAppendingMarker(type)) {
      // MapEntriesAppendingMarker.map since v3.0
      return createMapReader(findValueField(type, "map"));
    }
    return null;
  }

  @NoMuzzle
  private static String getSingleFieldAppendingMarkerName(Object logstashMarker) {
    SingleFieldAppendingMarker singleFieldAppendingMarker =
        (SingleFieldAppendingMarker) logstashMarker;
    return singleFieldAppendingMarker.getFieldName();
  }

  @Nullable
  private static FieldReader createStringReader(Field field) {
    if (field == null) {
      return null;
    }
    return (builder, logstashMarker, captureEventName) -> {
      String fieldName = getSingleFieldAppendingMarkerName(logstashMarker);
      Object fieldValue = extractFieldValue(field, logstashMarker);
      captureAttribute(builder, captureEventName, fieldName, fieldValue);
    };
  }

  @Nullable
  private static FieldReader createMapReader(Field field) {
    if (field == null) {
      return null;
    }
    return (attributes, logstashMarker, captureEventName) -> {
      Object fieldValue = extractFieldValue(field, logstashMarker);
      if (fieldValue instanceof Map) {
        Map<?, ?> map = (Map<?, ?>) fieldValue;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          Object key = entry.getKey();
          Object value = entry.getValue();
          captureAttribute(attributes, captureEventName, key, value);
        }
      }
    };
  }

  @Nullable
  private static Object extractFieldValue(Field field, Object logstashMarker) {
    try {
      return field.get(logstashMarker);
    } catch (IllegalAccessException e) {
      // ignore
    }
    return null;
  }

  @Nullable
  private static Field findValueField(Class<?> clazz, String... fieldNames) {
    for (String fieldName : fieldNames) {
      try {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
      } catch (NoSuchFieldException e) {
        // ignore
      }
    }
    return null;
  }

  private static boolean supportsLogstashMarkers() {
    try {
      Class.forName("net.logstash.logback.marker.LogstashMarker");
      Class.forName("net.logstash.logback.marker.SingleFieldAppendingMarker");
      Class.forName("net.logstash.logback.marker.MapEntriesAppendingMarker");
      // missing in some android versions, used for capturing logstash attributes
      Class.forName("java.lang.ClassValue");
    } catch (ClassNotFoundException e) {
      return false;
    }

    return true;
  }

  private static boolean supportsLogstashStructuredArguments() {
    try {
      Class.forName("net.logstash.logback.argument.StructuredArgument");
    } catch (ClassNotFoundException e) {
      return false;
    }

    return true;
  }

  @NoMuzzle
  private void captureLogstashStructuredArguments(LogRecordBuilder builder, Object[] arguments) {
    for (Object argument : arguments) {
      if (isLogstashSingleStructuredArgument(argument)) {
        captureLogstashSingleStructuredArgument(builder, argument);
      }
      if (isLogstashMapStructuredArgument(argument)) {
        captureLogstashMapStructuredArgument(builder, argument);
      }
    }
  }

  @NoMuzzle
  private static boolean isLogstashSingleStructuredArgument(Object argument) {
    // StructuredArguments implement the marker interface, so we can check for it
    // without importing the class directly (which may not be available at runtime)
    return argument instanceof SingleFieldAppendingMarker;
  }

  @NoMuzzle
  private void captureLogstashSingleStructuredArgument(LogRecordBuilder builder, Object argument) {
    // StructuredArguments created by v() or keyValue() extend SingleFieldAppendingMarker
    // which has getFieldName() and provides field value via reflection
    SingleFieldAppendingMarker marker = (SingleFieldAppendingMarker) argument;
    captureLogstashMarker(builder, marker);
  }

  @NoMuzzle
  private static boolean isLogstashMapStructuredArgument(Object argument) {
    // StructuredArguments implement the marker interface, so we can check for it
    // without importing the class directly (which may not be available at runtime)
    return argument instanceof MapEntriesAppendingMarker;
  }

  @NoMuzzle
  private void captureLogstashMapStructuredArgument(LogRecordBuilder builder, Object argument) {
    MapEntriesAppendingMarker marker = (MapEntriesAppendingMarker) argument;
    captureLogstashMarker(builder, marker);
  }

  private interface FieldReader {
    void read(LogRecordBuilder builder, Object logstashMarker, boolean captureEventName);
  }

  private static class LogstashFieldReaderHolder {
    // keeping this field in a separate class because ClassValue is missing in some android versions
    static final ClassValue<FieldReader> valueField =
        new ClassValue<FieldReader>() {
          @Override
          protected FieldReader computeValue(Class<?> type) {
            return createFieldReader(type);
          }
        };
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static final class Builder {
    private boolean captureExperimentalAttributes;
    private List<String> captureMdcAttributes = emptyList();
    private boolean captureCodeAttributes;
    private boolean captureMarkerAttribute;
    private boolean captureKeyValuePairAttributes;
    private boolean captureLoggerContext;
    private boolean captureArguments;
    private boolean captureLogstashMarkerAttributes;
    private boolean captureLogstashStructuredArguments;
    private boolean captureEventName;

    Builder() {}

    @CanIgnoreReturnValue
    public Builder setCaptureExperimentalAttributes(boolean captureExperimentalAttributes) {
      this.captureExperimentalAttributes = captureExperimentalAttributes;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setCaptureMdcAttributes(List<String> captureMdcAttributes) {
      this.captureMdcAttributes = captureMdcAttributes;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setCaptureCodeAttributes(boolean captureCodeAttributes) {
      this.captureCodeAttributes = captureCodeAttributes;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setCaptureMarkerAttribute(boolean captureMarkerAttribute) {
      this.captureMarkerAttribute = captureMarkerAttribute;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setCaptureKeyValuePairAttributes(boolean captureKeyValuePairAttributes) {
      this.captureKeyValuePairAttributes = captureKeyValuePairAttributes;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setCaptureLoggerContext(boolean captureLoggerContext) {
      this.captureLoggerContext = captureLoggerContext;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setCaptureArguments(boolean captureArguments) {
      this.captureArguments = captureArguments;
      return this;
    }

    /**
     * @deprecated Use {@link #setCaptureLogstashMarkerAttributes(boolean)} instead. This method is
     *     deprecated and will be removed in a future release.
     */
    @Deprecated
    @CanIgnoreReturnValue
    public Builder setCaptureLogstashAttributes(boolean captureLogstashAttributes) {
      return setCaptureLogstashMarkerAttributes(captureLogstashAttributes);
    }

    @CanIgnoreReturnValue
    public Builder setCaptureLogstashMarkerAttributes(boolean captureLogstashMarkerAttributes) {
      this.captureLogstashMarkerAttributes = captureLogstashMarkerAttributes;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setCaptureLogstashStructuredArguments(
        boolean captureLogstashStructuredArguments) {
      this.captureLogstashStructuredArguments = captureLogstashStructuredArguments;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setCaptureEventName(boolean captureEventName) {
      this.captureEventName = captureEventName;
      return this;
    }

    public LoggingEventMapper build() {
      return new LoggingEventMapper(this);
    }
  }
}
