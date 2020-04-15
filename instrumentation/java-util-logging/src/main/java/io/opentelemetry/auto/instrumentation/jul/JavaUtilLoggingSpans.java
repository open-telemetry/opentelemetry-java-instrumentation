/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.jul;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.config.Config;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JavaUtilLoggingSpans {

  private static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.java-util-logging");

  private static final Formatter FORMATTER = new AccessibleFormatter();

  public static void capture(final Logger logger, final LogRecord logRecord) {

    final Level level = logRecord.getLevel();
    if (!logger.isLoggable(level)) {
      // this is already checked in most cases, except if Logger.log(LogRecord) was called directly
      return;
    }
    if (level.intValue() < getThreshold().intValue()) {
      return;
    }

    final Throwable t = logRecord.getThrown();
    final Span span = TRACER.spanBuilder("log.message").startSpan();
    span.setAttribute("message", FORMATTER.formatMessage(logRecord));
    span.setAttribute("level", level.getName());
    span.setAttribute("loggerName", logger.getName());
    if (t != null) {
      span.setAttribute("error.stack", toString(t));
    }
    span.end();
  }

  private static String toString(final Throwable t) {
    final StringWriter out = new StringWriter();
    t.printStackTrace(new PrintWriter(out));
    return out.toString();
  }

  private static Level getThreshold() {
    final String level = Config.get().getExperimentalLogCaptureThreshold();
    if (level == null) {
      return Level.OFF;
    }
    switch (level) {
      case "OFF":
        return Level.OFF;
      case "FATAL":
      case "ERROR":
      case "SEVERE":
        return Level.SEVERE;
      case "WARN":
      case "WARNING":
        return Level.WARNING;
      case "INFO":
        return Level.INFO;
      case "CONFIG":
        return Level.CONFIG;
      case "DEBUG":
      case "FINE":
        return Level.FINE;
      case "FINER":
        return Level.FINER;
      case "TRACE":
      case "FINEST":
        return Level.FINEST;
      case "ALL":
        return Level.ALL;
      default:
        log.error("unexpected value for {}: {}", Config.EXPERIMENTAL_LOG_CAPTURE_THRESHOLD, level);
        return Level.OFF;
    }
  }

  // this is just needed for calling formatMessage in abstract super class
  public static class AccessibleFormatter extends Formatter {

    @Override
    public String format(final LogRecord record) {
      throw new UnsupportedOperationException();
    }
  }
}
