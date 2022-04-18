/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jul;

import application.java.util.logging.Logger;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.appender.internal.LogBuilder;
import io.opentelemetry.instrumentation.api.appender.internal.Severity;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.bootstrap.AgentLogEmitterProvider;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public final class JavaUtilLoggingHelper {

  private static final Formatter FORMATTER = new AccessibleFormatter();

  private static final boolean captureExperimentalAttributes =
      Config.get()
          .getBoolean("otel.instrumentation.java-util-logging.experimental-log-attributes", false);

  public static void capture(Logger logger, LogRecord logRecord) {

    if (!logger.isLoggable(logRecord.getLevel())) {
      // this is already checked in most cases, except if Logger.log(LogRecord) was called directly
      return;
    }

    String instrumentationName = logger.getName();
    if (instrumentationName == null || instrumentationName.isEmpty()) {
      instrumentationName = "ROOT";
    }
    LogBuilder builder =
        AgentLogEmitterProvider.get().logEmitterBuilder(instrumentationName).build().logBuilder();
    mapLogRecord(builder, logRecord);
    builder.emit();
  }

  /**
   * Map the {@link LogRecord} data model onto the {@link LogBuilder}. Unmapped fields include:
   *
   * <ul>
   *   <li>Fully qualified class name - {@link LogRecord#getSourceClassName()}
   *   <li>Fully qualified method name - {@link LogRecord#getSourceMethodName()}
   *   <li>Thread id - {@link LogRecord#getThreadID()}
   * </ul>
   */
  private static void mapLogRecord(LogBuilder builder, LogRecord logRecord) {
    // message
    String message = FORMATTER.formatMessage(logRecord);
    if (message != null) {
      builder.setBody(message);
    }

    // time
    // TODO (trask) use getInstant() for more precision on Java 9
    long timestamp = logRecord.getMillis();
    builder.setEpoch(timestamp, TimeUnit.MILLISECONDS);

    // level
    Level level = logRecord.getLevel();
    if (level != null) {
      builder.setSeverity(levelToSeverity(level));
      builder.setSeverityText(logRecord.getLevel().getName());
    }

    AttributesBuilder attributes = Attributes.builder();

    // throwable
    Throwable throwable = logRecord.getThrown();
    if (throwable != null) {
      // TODO (trask) extract method for recording exception into
      // instrumentation-appender-api-internal
      attributes.put(SemanticAttributes.EXCEPTION_TYPE, throwable.getClass().getName());
      attributes.put(SemanticAttributes.EXCEPTION_MESSAGE, throwable.getMessage());
      StringWriter writer = new StringWriter();
      throwable.printStackTrace(new PrintWriter(writer));
      attributes.put(SemanticAttributes.EXCEPTION_STACKTRACE, writer.toString());
    }

    if (captureExperimentalAttributes) {
      Thread currentThread = Thread.currentThread();
      attributes.put(SemanticAttributes.THREAD_NAME, currentThread.getName());
      attributes.put(SemanticAttributes.THREAD_ID, currentThread.getId());
    }

    builder.setAttributes(attributes.build());

    // span context
    builder.setContext(Context.current());
  }

  private static Severity levelToSeverity(Level level) {
    int lev = level.intValue();
    if (lev <= Level.FINEST.intValue()) {
      return Severity.TRACE;
    }
    if (lev <= Level.FINER.intValue()) {
      return Severity.DEBUG;
    }
    if (lev <= Level.FINE.intValue()) {
      return Severity.DEBUG2;
    }
    if (lev <= Level.CONFIG.intValue()) {
      return Severity.DEBUG3;
    }
    if (lev <= Level.INFO.intValue()) {
      return Severity.INFO;
    }
    if (lev <= Level.WARNING.intValue()) {
      return Severity.WARN;
    }
    if (lev <= Level.SEVERE.intValue()) {
      return Severity.ERROR;
    }
    return Severity.FATAL;
  }

  // this is just needed for calling formatMessage in abstract super class
  private static class AccessibleFormatter extends Formatter {

    @Override
    public String format(LogRecord record) {
      throw new UnsupportedOperationException();
    }
  }

  private JavaUtilLoggingHelper() {}
}
