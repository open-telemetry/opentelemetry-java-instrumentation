/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.springboot13;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Scope;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class LogCorrelationApplication {
  private static final Logger logger = LogManager.getLogger(LogCorrelationApplication.class);

  @SuppressWarnings("SystemOut")
  public static void main(String[] args) {
    ClassLoader loader = LogCorrelationApplication.class.getClassLoader();
    if (!loader
        .getClass()
        .getName()
        .equals("org.springframework.boot.loader.LaunchedURLClassLoader")) {
      throw new AssertionError("Unexpected application loader: " + loader);
    }
    Span span =
        GlobalOpenTelemetry.getTracer("legacy-spring-boot-smoke")
            .spanBuilder("log correlation")
            .startSpan();
    SpanContext spanContext = span.getSpanContext();
    if (!spanContext.isValid()) {
      throw new AssertionError("The OpenTelemetry API did not create a valid span");
    }

    System.out.println("EXPECTED_IDS " + spanContext.getTraceId() + " " + spanContext.getSpanId());
    try (Scope ignored = span.makeCurrent()) {
      logger.info("correlated log message");
    } finally {
      span.end();
    }
  }

  private LogCorrelationApplication() {}
}
