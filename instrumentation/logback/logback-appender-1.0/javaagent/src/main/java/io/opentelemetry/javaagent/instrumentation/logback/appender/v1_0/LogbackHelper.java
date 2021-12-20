/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.appender.v1_0;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.appender.GlobalLogEmitterProvider;
import io.opentelemetry.instrumentation.api.appender.LogBuilder;
import io.opentelemetry.instrumentation.api.appender.Severity;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;

public final class LogbackHelper {

  public static void capture(final ILoggingEvent event) {
    LogBuilder builder =
        GlobalLogEmitterProvider.get()
            .logEmitterBuilder(event.getLoggerName())
            .build()
            .logBuilder();
    mapLoggingEvent(builder, event);
    builder.emit();
  }

  /**
   * Map the {@link ILoggingEvent} data model onto the {@link LogBuilder}. Unmapped fields include:
   *
   * <ul>
   *   <li>Thread name - {@link ILoggingEvent#getThreadName()}
   *   <li>Marker - {@link ILoggingEvent#getMarker()}
   *   <li>Mapped diagnostic context - {@link ILoggingEvent#getMDCPropertyMap()}
   * </ul>
   */
  private static void mapLoggingEvent(LogBuilder builder, ILoggingEvent loggingEvent) {
    // message
    String message = loggingEvent.getMessage();
    if (message != null) {
      builder.setBody(message);
    }

    // time
    long timestamp = loggingEvent.getTimeStamp();
    builder.setEpoch(timestamp, TimeUnit.MILLISECONDS);

    // level
    Level level = loggingEvent.getLevel();
    if (level != null) {
      builder.setSeverity(levelToSeverity(level));
      builder.setSeverityText(loggingEvent.getLevel().levelStr);
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
      AttributesBuilder attributes = Attributes.builder();

      // TODO (trask) extract method for recording exception into instrumentation-api-appender
      attributes.put(SemanticAttributes.EXCEPTION_TYPE, throwable.getClass().getName());
      attributes.put(SemanticAttributes.EXCEPTION_MESSAGE, throwable.getMessage());
      StringWriter writer = new StringWriter();
      throwable.printStackTrace(new PrintWriter(writer));
      attributes.put(SemanticAttributes.EXCEPTION_STACKTRACE, writer.toString());

      builder.setAttributes(attributes.build());
    }

    // span context
    builder.setContext(Context.current());
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

  private LogbackHelper() {}
}
