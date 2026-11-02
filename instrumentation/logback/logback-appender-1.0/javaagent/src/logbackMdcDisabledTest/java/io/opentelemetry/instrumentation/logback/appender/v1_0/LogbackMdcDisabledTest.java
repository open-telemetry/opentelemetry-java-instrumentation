/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

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
  void exportsApplicationTraceIdWhenLogbackMdcIsDisabled() {
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
                .hasAttributesSatisfying(equalTo(stringKey("trace_id"), "application-value")));
  }
}
