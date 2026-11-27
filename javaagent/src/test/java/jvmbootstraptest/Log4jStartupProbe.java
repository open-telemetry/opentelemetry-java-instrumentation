/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jvmbootstraptest;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Scope;
import org.apache.logging.log4j.CloseableThreadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.spi.ReadOnlyThreadContextMap;

public class Log4jStartupProbe {
  public static void main(String[] args) {
    Logger logger = LogManager.getLogger(Log4jStartupProbe.class);
    ReadOnlyThreadContextMap map = ThreadContext.getThreadContextMap();
    String mapClass = map == null ? "default" : map.getClass().getSimpleName();
    if (!mapClass.equals(args[1])) {
      throw new IllegalStateException(
          "Expected thread context map " + args[1] + ", got " + mapClass);
    }
    logSpan("first span", Boolean.parseBoolean(args[0]));
    logSpan("second span", false);
    logger.info("outside span");
    LogManager.shutdown();
  }

  @SuppressWarnings("SystemOut") // The parent test captures the probe's output.
  private static void logSpan(String name, boolean withApplicationSpanId) {
    Logger logger = LogManager.getLogger(Log4jStartupProbe.class);
    Span span = GlobalOpenTelemetry.getTracer("log4j-startup-test").spanBuilder(name).startSpan();
    try {
      SpanContext context = span.getSpanContext();
      if (!context.isValid()) {
        throw new IllegalStateException("The probe did not get a valid span from the agent");
      }
      System.out.printf(
          "SPAN|%s|%s|%s%n",
          context.getTraceId(), context.getSpanId(), context.getTraceFlags().asHex());
      try (Scope ignored = span.makeCurrent();
          CloseableThreadContext.Instance threadContext =
              CloseableThreadContext.put("request", "application-value")) {
        logger.info(name);
        if (withApplicationSpanId) {
          try (CloseableThreadContext.Instance spanId =
              CloseableThreadContext.put("span_id", "application-span-id")) {
            logger.info("inside span with application span ID");
          }
        }
      }
    } finally {
      span.end();
    }
  }

  private Log4jStartupProbe() {}
}
