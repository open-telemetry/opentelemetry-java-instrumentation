/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.appender.v1_2;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
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

public final class LogEventMapper {

  private static final Cache<String, AttributeKey<String>> mdcAttributeKeys = Cache.bounded(100);

  public static final LogEventMapper INSTANCE = new LogEventMapper();

  // copied from org.apache.log4j.Level because it was only introduced in 1.2.12
  private static final int TRACE_INT = 5000;

  private static final boolean captureExperimentalAttributes =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.log4j-appender.experimental-log-attributes", false);

  private final Map<String, AttributeKey<String>> captureMdcAttributes;

  // cached as an optimization
  private final boolean captureAllMdcAttributes;

  private LogEventMapper() {
    List<String> captureMdcAttributes =
        AgentInstrumentationConfig.get()
            .getList(
                "otel.instrumentation.log4j-appender.experimental.capture-mdc-attributes",
                emptyList());
    this.captureMdcAttributes =
        captureMdcAttributes.stream()
            .collect(Collectors.toMap(attr -> attr, LogEventMapper::getMdcAttributeKey));
    this.captureAllMdcAttributes =
        captureMdcAttributes.size() == 1 && captureMdcAttributes.get(0).equals("*");
  }

  public void capture(Category logger, Priority level, Object message, Throwable throwable) {
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

    AttributesBuilder attributes = Attributes.builder();

    // throwable
    if (throwable != null) {
      // TODO (trask) extract method for recording exception into
      // io.opentelemetry:opentelemetry-api
      attributes.put(ExceptionAttributes.EXCEPTION_TYPE, throwable.getClass().getName());
      attributes.put(ExceptionAttributes.EXCEPTION_MESSAGE, throwable.getMessage());
      StringWriter writer = new StringWriter();
      throwable.printStackTrace(new PrintWriter(writer));
      attributes.put(ExceptionAttributes.EXCEPTION_STACKTRACE, writer.toString());
    }

    captureMdcAttributes(attributes);

    if (captureExperimentalAttributes) {
      Thread currentThread = Thread.currentThread();
      attributes.put(ThreadIncubatingAttributes.THREAD_NAME, currentThread.getName());
      attributes.put(ThreadIncubatingAttributes.THREAD_ID, currentThread.getId());
    }

    builder.setAllAttributes(attributes.build());

    // span context
    builder.setContext(Context.current());

    builder.setTimestamp(Instant.now());
    builder.emit();
  }

  private void captureMdcAttributes(AttributesBuilder attributes) {

    Hashtable<?, ?> context = MDC.getContext();

    if (captureAllMdcAttributes) {
      if (context != null) {
        for (Map.Entry<?, ?> entry : context.entrySet()) {
          attributes.put(
              getMdcAttributeKey(String.valueOf(entry.getKey())), String.valueOf(entry.getValue()));
        }
      }
      return;
    }

    for (Map.Entry<String, AttributeKey<String>> entry : captureMdcAttributes.entrySet()) {
      Object value = context.get(entry.getKey());
      if (value != null) {
        attributes.put(entry.getValue(), value.toString());
      }
    }
  }

  private static AttributeKey<String> getMdcAttributeKey(String key) {
    return mdcAttributeKeys.computeIfAbsent(key, AttributeKey::stringKey);
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
