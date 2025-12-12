/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.appender.v1_2;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.semconv.CodeAttributes;
import io.opentelemetry.semconv.ExceptionAttributes;
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.log4j.Category;
import org.apache.log4j.MDC;
import org.apache.log4j.Priority;
import org.apache.log4j.spi.LocationInfo;

public final class LogEventMapper {

  private static final Cache<String, AttributeKey<String>> mdcAttributeKeys = Cache.bounded(100);

  public static final LogEventMapper INSTANCE = new LogEventMapper();

  private static final AttributeKey<String> CODE_FILEPATH = AttributeKey.stringKey("code.filepath");
  private static final AttributeKey<String> CODE_FUNCTION = AttributeKey.stringKey("code.function");
  private static final AttributeKey<Long> CODE_LINENO = AttributeKey.longKey("code.lineno");
  private static final AttributeKey<String> CODE_NAMESPACE =
      AttributeKey.stringKey("code.namespace");
  // copied from EventIncubatingAttributes
  private static final AttributeKey<String> EVENT_NAME = AttributeKey.stringKey("event.name");
  // copied from org.apache.log4j.Level because it was only introduced in 1.2.12
  private static final int TRACE_INT = 5000;

  private static final boolean captureExperimentalAttributes =
      DeclarativeConfigUtil.getBoolean(
              GlobalOpenTelemetry.get(), "java", "log4j-appender", "experimental_log_attributes")
          .orElse(false);

  private final Map<String, AttributeKey<String>> captureMdcAttributes;

  // cached as an optimization
  private final boolean captureAllMdcAttributes;

  private final boolean captureEventName =
      DeclarativeConfigUtil.getBoolean(
              GlobalOpenTelemetry.get(),
              "java",
              "log4j-appender",
              "experimental",
              "capture_event_name")
          .orElse(false);

  private LogEventMapper() {
    List<String> captureMdcAttributes =
        DeclarativeConfigUtil.getList(
                GlobalOpenTelemetry.get(),
                "java",
                "log4j-appender",
                "experimental",
                "capture_mdc_attributes")
            .orElse(emptyList());
    this.captureMdcAttributes =
        captureMdcAttributes.stream()
            .collect(Collectors.toMap(attr -> attr, LogEventMapper::getMdcAttributeKey));
    this.captureAllMdcAttributes =
        captureMdcAttributes.size() == 1 && captureMdcAttributes.get(0).equals("*");
  }

  boolean captureCodeAttributes =
      DeclarativeConfigUtil.getBoolean(
              GlobalOpenTelemetry.get(),
              "java",
              "log4j-appender",
              "experimental",
              "capture_code_attributes")
          .orElse(false);

  public void capture(
      String fqcn, Category logger, Priority level, Object message, Throwable throwable) {
    String instrumentationName = logger.getName();
    if (instrumentationName == null || instrumentationName.isEmpty()) {
      instrumentationName = "ROOT";
    }
    LogRecordBuilder builder =
        GlobalOpenTelemetry.get()
            .getLogsBridge()
            .loggerBuilder(instrumentationName)
            .build()
            .logRecordBuilder();

    // message
    if (message != null) {
      builder.setBody(String.valueOf(message));
    }

    // level
    if (level != null) {
      builder.setSeverity(levelToSeverity(level));
      builder.setSeverityText(level.toString());
    }

    // throwable
    if (throwable != null) {
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

    captureMdcAttributes(builder);

    if (captureExperimentalAttributes) {
      Thread currentThread = Thread.currentThread();
      builder.setAttribute(ThreadIncubatingAttributes.THREAD_NAME, currentThread.getName());
      builder.setAttribute(ThreadIncubatingAttributes.THREAD_ID, currentThread.getId());
    }

    if (captureCodeAttributes) {
      LocationInfo locationInfo = new LocationInfo(new Throwable(), fqcn);
      String fileName = locationInfo.getFileName();
      if (fileName != null) {
        if (SemconvStability.isEmitStableCodeSemconv()) {
          builder.setAttribute(CodeAttributes.CODE_FILE_PATH, fileName);
        }
        if (SemconvStability.isEmitOldCodeSemconv()) {
          builder.setAttribute(CODE_FILEPATH, fileName);
        }
      }

      if (SemconvStability.isEmitStableCodeSemconv()) {
        builder.setAttribute(
            CodeAttributes.CODE_FUNCTION_NAME,
            locationInfo.getClassName() + "." + locationInfo.getMethodName());
      }
      if (SemconvStability.isEmitOldCodeSemconv()) {
        builder.setAttribute(CODE_NAMESPACE, locationInfo.getClassName());
        builder.setAttribute(CODE_FUNCTION, locationInfo.getMethodName());
      }

      String lineNumber = locationInfo.getLineNumber();
      int codeLineNo = -1;
      if (!lineNumber.equals("?")) {
        try {
          codeLineNo = Integer.parseInt(lineNumber);
        } catch (NumberFormatException e) {
          // ignore
        }
      }
      if (codeLineNo >= 0) {
        if (SemconvStability.isEmitStableCodeSemconv()) {
          builder.setAttribute(CodeAttributes.CODE_LINE_NUMBER, (long) codeLineNo);
        }
        if (SemconvStability.isEmitOldCodeSemconv()) {
          builder.setAttribute(CODE_LINENO, (long) codeLineNo);
        }
      }
    }

    // span context
    builder.setContext(Context.current());

    builder.setTimestamp(Instant.now());
    builder.emit();
  }

  private void captureMdcAttributes(LogRecordBuilder builder) {

    Hashtable<?, ?> context = MDC.getContext();

    if (captureAllMdcAttributes) {
      if (context != null) {
        for (Map.Entry<?, ?> entry : context.entrySet()) {
          setAttributeOrEventName(
              builder, getMdcAttributeKey(String.valueOf(entry.getKey())), entry.getValue());
        }
      }
      return;
    }

    for (Map.Entry<String, AttributeKey<String>> entry : captureMdcAttributes.entrySet()) {
      Object value = context.get(entry.getKey());
      setAttributeOrEventName(builder, entry.getValue(), value);
    }
  }

  private static AttributeKey<String> getMdcAttributeKey(String key) {
    return mdcAttributeKeys.computeIfAbsent(key, AttributeKey::stringKey);
  }

  private void setAttributeOrEventName(
      LogRecordBuilder builder, AttributeKey<String> key, Object value) {
    if (value != null) {
      if (captureEventName && key.equals(EVENT_NAME)) {
        builder.setEventName(value.toString());
      } else {
        builder.setAttribute(key, value.toString());
      }
    }
  }

  private static Severity levelToSeverity(Priority level) {
    int lev = level.toInt();
    if (lev <= TRACE_INT) {
      return Severity.TRACE;
    }
    if (lev <= Priority.DEBUG_INT) {
      return Severity.DEBUG;
    }
    if (lev <= Priority.INFO_INT) {
      return Severity.INFO;
    }
    if (lev <= Priority.WARN_INT) {
      return Severity.WARN;
    }
    if (lev <= Priority.ERROR_INT) {
      return Severity.ERROR;
    }
    return Severity.FATAL;
  }
}
