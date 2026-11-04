/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FILE_PATH;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FUNCTION_NAME;
import static io.opentelemetry.semconv.CodeAttributes.CODE_LINE_NUMBER;
import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_ID;
import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_NAME;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class LogbackMdcLoggingKeysTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = LoggerFactory.getLogger("test");

  @Test
  void skipsConfiguredTraceContextKeys() {
    testing.runWithSpan(
        "parent",
        () -> {
          try (Scope ignored =
              Baggage.builder().put("baggage_key", "baggage_value").build().makeCurrent()) {
            MDC.put("trace_id", "application-value");
            try {
              logger.info("log message");
            } finally {
              MDC.clear();
            }
          }
        });

    // the test task configures the trace_id_test, span_id_test and trace_flags_test keys, so
    // trace_id is an application entry
    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasBody("log message")
                .hasSpanContext(testing.spans().get(0).getSpanContext())
                .hasAttributesSatisfyingExactly(
                    equalTo(stringKey("baggage.baggage_key"), "baggage_value"),
                    equalTo(stringKey("trace_id"), "application-value"),
                    equalTo(
                        CODE_FILE_PATH, LogbackMdcLoggingKeysTest.class.getSimpleName() + ".java"),
                    satisfies(
                        CODE_FUNCTION_NAME,
                        val -> val.startsWith(LogbackMdcLoggingKeysTest.class.getName())),
                    satisfies(CODE_LINE_NUMBER, val -> val.isPositive()),
                    equalTo(THREAD_NAME, Thread.currentThread().getName()),
                    equalTo(THREAD_ID, Thread.currentThread().getId())));
  }
}
