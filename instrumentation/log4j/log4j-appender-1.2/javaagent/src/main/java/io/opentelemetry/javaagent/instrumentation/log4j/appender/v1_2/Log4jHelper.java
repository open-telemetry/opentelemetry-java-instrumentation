/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.appender.v1_2;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.appender.api.internal.AgentLogEmitterProvider;
import io.opentelemetry.instrumentation.appender.api.internal.LogBuilder;
import io.opentelemetry.instrumentation.appender.api.internal.Severity;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.apache.log4j.Category;
import org.apache.log4j.Priority;

public final class Log4jHelper {

  // copied from org.apache.log4j.Level because it was only introduced in 1.2.12
  private static final int TRACE_INT = 5000;

  // TODO (trask) capture MDC
  public static void capture(Category logger, Priority level, Object message, Throwable throwable) {
    LogBuilder builder =
        AgentLogEmitterProvider.get().logEmitterBuilder(logger.getName()).build().logBuilder();

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
      AttributesBuilder attributes = Attributes.builder();

      // TODO (trask) extract method for recording exception into
      // instrumentation-appender-api-internal
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

  private Log4jHelper() {}
}
