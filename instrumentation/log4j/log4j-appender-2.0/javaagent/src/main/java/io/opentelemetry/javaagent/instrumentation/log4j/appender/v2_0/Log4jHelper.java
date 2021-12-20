/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.appender.v2_0;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.appender.GlobalLogEmitterProvider;
import io.opentelemetry.instrumentation.api.appender.LogBuilder;
import io.opentelemetry.instrumentation.api.appender.Severity;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.Message;

public class Log4jHelper {

  public static void capture(Logger logger, Level level, Message message, Throwable throwable) {

    LogBuilder builder =
        GlobalLogEmitterProvider.get().logEmitterBuilder(logger.getName()).build().logBuilder();

    // message
    if (message != null) {
      builder.setBody(message.getFormattedMessage());
    }

    // level
    if (level != null) {
      builder.setSeverity(levelToSeverity(level));
      builder.setSeverityText(level.toString());
    }

    // throwable
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

    builder.emit();
  }

  private static Severity levelToSeverity(Level level) {
    switch (level.getStandardLevel()) {
      case ALL:
        return Severity.TRACE;
      case TRACE:
        return Severity.TRACE2;
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
