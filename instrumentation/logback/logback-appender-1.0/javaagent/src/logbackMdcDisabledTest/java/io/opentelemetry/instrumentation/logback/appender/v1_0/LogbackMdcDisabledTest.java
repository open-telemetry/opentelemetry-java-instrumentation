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

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class LogbackMdcDisabledTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = LoggerFactory.getLogger("test");

  @Test
  void capturesApplicationTraceIdKey() {
    MDC.put("trace_id", "application-value");
    try {
      logger.info("log message");
    } finally {
      MDC.clear();
    }

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasBody("log message")
                .hasAttributesSatisfyingExactly(
                    equalTo(stringKey("trace_id"), "application-value"),
                    equalTo(CODE_FILE_PATH, LogbackMdcDisabledTest.class.getSimpleName() + ".java"),
                    equalTo(
                        CODE_FUNCTION_NAME,
                        LogbackMdcDisabledTest.class.getName() + ".capturesApplicationTraceIdKey"),
                    satisfies(CODE_LINE_NUMBER, val -> val.isPositive()),
                    equalTo(THREAD_NAME, Thread.currentThread().getName()),
                    equalTo(THREAD_ID, Thread.currentThread().getId())));
  }
}
