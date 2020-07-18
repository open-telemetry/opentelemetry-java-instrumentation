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

package io.opentelemetry.auto.instrumentation.log4j.v1_1;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.config.Config;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.io.PrintWriter;
import java.io.StringWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.log4j.Category;
import org.apache.log4j.Priority;

@Slf4j
public class Log4jSpans {

  private static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.log4j-1.1");

  // these constants are copied from org.apache.log4j.Priority and org.apache.log4j.Level because
  // Level was only introduced in 1.2, and then Level.TRACE was only introduced in 1.2.12
  private static final int OFF_INT = Integer.MAX_VALUE;
  private static final int FATAL_INT = 50000;
  private static final int ERROR_INT = 40000;
  private static final int WARN_INT = 30000;
  private static final int INFO_INT = 20000;
  private static final int DEBUG_INT = 10000;
  private static final int TRACE_INT = 5000;
  private static final int ALL_INT = Integer.MIN_VALUE;

  public static void capture(
      final Category logger, final Priority level, final Object message, final Throwable t) {

    if (level.toInt() < getThreshold()) {
      return;
    }

    Span span = TRACER.spanBuilder("log.message").startSpan();
    span.setAttribute("message", String.valueOf(message));
    span.setAttribute("level", level.toString());
    span.setAttribute("loggerName", logger.getName());
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

  private static int getThreshold() {
    String level = Config.get().getExperimentalLogCaptureThreshold();
    if (level == null) {
      return OFF_INT;
    }
    switch (level) {
      case "OFF":
        return OFF_INT;
      case "FATAL":
        return FATAL_INT;
      case "ERROR":
      case "SEVERE":
        return ERROR_INT;
      case "WARN":
      case "WARNING":
        return WARN_INT;
      case "INFO":
        return INFO_INT;
      case "CONFIG":
      case "DEBUG":
      case "FINE":
      case "FINER":
        return DEBUG_INT;
      case "TRACE":
      case "FINEST":
        return TRACE_INT;
      case "ALL":
        return ALL_INT;
      default:
        log.error("unexpected value for {}: {}", Config.EXPERIMENTAL_LOG_CAPTURE_THRESHOLD, level);
        return OFF_INT;
    }
  }
}
