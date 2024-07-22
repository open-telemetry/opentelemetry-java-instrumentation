/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jbosslogmanager.appender.v1_1;

import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

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
import java.util.List;
import java.util.Map;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.MDC;

public final class LoggingEventMapper {

  public static final LoggingEventMapper INSTANCE = new LoggingEventMapper();

  private static final Cache<String, AttributeKey<String>> mdcAttributeKeys = Cache.bounded(100);

  private final List<String> captureMdcAttributes;

  private static final boolean captureExperimentalAttributes =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.jboss-logmanager.experimental-log-attributes", false);

  // cached as an optimization
  private final boolean captureAllMdcAttributes;

  private LoggingEventMapper() {
    this.captureMdcAttributes =
        AgentInstrumentationConfig.get()
            .getList(
                "otel.instrumentation.jboss-logmanager.experimental.capture-mdc-attributes",
                emptyList());
    this.captureAllMdcAttributes =
        captureMdcAttributes.size() == 1 && captureMdcAttributes.get(0).equals("*");
  }

  public void capture(Logger logger, ExtLogRecord record) {
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

    String message = record.getFormattedMessage();
    if (message != null) {
      builder.setBody(message);
    }

    java.util.logging.Level level = record.getLevel();
    if (level != null) {
      builder.setSeverity(levelToSeverity(level));
      builder.setSeverityText(level.toString());
    }

    AttributesBuilder attributes = Attributes.builder();

    Throwable throwable = record.getThrown();
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

    builder.setContext(Context.current());

    builder.setTimestamp(record.getMillis(), MILLISECONDS);
    builder.emit();
  }

  private void captureMdcAttributes(AttributesBuilder attributes) {

    Map<String, String> context = MDC.copy();

    if (captureAllMdcAttributes) {
      if (context != null) {
        for (Map.Entry<String, String> entry : context.entrySet()) {
          attributes.put(
              getMdcAttributeKey(String.valueOf(entry.getKey())), String.valueOf(entry.getValue()));
        }
      }
      return;
    }

    for (String key : captureMdcAttributes) {
      Object value = context.get(key);
      if (value != null) {
        attributes.put(key, value.toString());
      }
    }
  }

  public static AttributeKey<String> getMdcAttributeKey(String key) {
    return mdcAttributeKeys.computeIfAbsent(key, AttributeKey::stringKey);
  }

  private static Severity levelToSeverity(java.util.logging.Level level) {
    int levelInt = level.intValue();
    if (levelInt >= Level.FATAL.intValue()) {
      return Severity.FATAL;
    } else if (levelInt >= Level.ERROR.intValue()) {
      return Severity.ERROR;
    } else if (levelInt >= Level.WARNING.intValue()) {
      return Severity.WARN;
    } else if (levelInt >= Level.INFO.intValue()) {
      return Severity.INFO;
    } else if (levelInt >= Level.DEBUG.intValue()) {
      return Severity.DEBUG;
    } else if (levelInt >= Level.TRACE.intValue()) {
      return Severity.TRACE;
    }
    return Severity.UNDEFINED_SEVERITY_NUMBER;
  }
}
