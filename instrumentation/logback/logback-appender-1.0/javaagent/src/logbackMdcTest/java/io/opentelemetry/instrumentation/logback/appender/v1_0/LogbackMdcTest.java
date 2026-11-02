/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LogbackMdcTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = LoggerFactory.getLogger("test");

  @Test
  void exportsBaggageAddedToMdc() {
    testing.runWithSpan(
        "parent",
        () -> {
          try (Scope ignored =
              Baggage.builder().put("baggage_key", "baggage_value").build().makeCurrent()) {
            logger.info("log message");
          }
        });

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasBody("log message")
                .hasSpanContext(testing.spans().get(0).getSpanContext())
                // the trace context is already exported in dedicated fields
                .hasAttributesSatisfying(
                    equalTo(stringKey("baggage.baggage_key"), "baggage_value"),
                    equalTo(stringKey("trace_id"), null),
                    equalTo(stringKey("span_id"), null),
                    equalTo(stringKey("trace_flags"), null)));
  }
}
