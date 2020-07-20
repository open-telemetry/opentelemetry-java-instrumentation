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

package io.opentelemetry.auto.instrumentation.log4j.v2_0;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.config.Config;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.Message;
import org.slf4j.LoggerFactory;

public class Log4jSpans {

  private static final org.slf4j.Logger log = LoggerFactory.getLogger(Log4jSpans.class);

  private static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.log4j-2.0");

  public static void capture(
      final Logger logger, final Level level, final Message message, final Throwable t) {

    if (level.intLevel() > getThreshold().intLevel()) {
      return;
    }

    Span span =
        TRACER
            .spanBuilder("log.message")
            .setAttribute("message", message.getFormattedMessage())
            .setAttribute("level", level.toString())
            .setAttribute("loggerName", logger.getName())
            .startSpan();
    if (t != null) {
      span.setAttribute("error.stack", toString(t));
    }
    span.end();
  }

  private static String toString(final Throwable t) {
    StringWriter out = new StringWriter();
    t.printStackTrace(new PrintWriter(out));
    return out.toString();
  }

  private static Level getThreshold() {
    String level = Config.get().getExperimentalLogCaptureThreshold();
    if (level == null) {
      return Level.OFF;
    }
    switch (level) {
      case "OFF":
        return Level.OFF;
      case "FATAL":
        return Level.FATAL;
      case "ERROR":
      case "SEVERE":
        return Level.ERROR;
      case "WARN":
      case "WARNING":
        return Level.WARN;
      case "INFO":
        return Level.INFO;
      case "CONFIG":
      case "DEBUG":
      case "FINE":
      case "FINER":
        return Level.DEBUG;
      case "TRACE":
      case "FINEST":
        return Level.TRACE;
      case "ALL":
        return Level.ALL;
      default:
        log.error("unexpected value for {}: {}", Config.EXPERIMENTAL_LOG_CAPTURE_THRESHOLD, level);
        return Level.OFF;
    }
  }
}
