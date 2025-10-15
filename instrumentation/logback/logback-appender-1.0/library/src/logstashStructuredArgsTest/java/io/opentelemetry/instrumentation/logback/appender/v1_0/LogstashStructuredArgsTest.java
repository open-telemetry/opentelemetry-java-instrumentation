/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import net.logstash.logback.argument.StructuredArguments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LogstashStructuredArgsTest {
  private static final Logger logger = LoggerFactory.getLogger("TestLogger");

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  @BeforeAll
  static void setupAll() {
    OpenTelemetryAppender.install(testing.getOpenTelemetry());
  }

  @Test
  void basicStructuredArgumentWithV() {
    logger.info("Basic structured arg: {}", StructuredArguments.v("customer_id", "123"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasBody("Basic structured arg: 123")
                .hasAttributesSatisfying(equalTo(AttributeKey.stringKey("customer_id"), "123")));
  }

  @Test
  void structuredArgumentWithKeyValue() {
    logger.info("Processing order: {}", StructuredArguments.keyValue("order_id", "ORD-456"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasBody("Processing order: order_id=ORD-456")
                .hasAttributesSatisfying(equalTo(AttributeKey.stringKey("order_id"), "ORD-456")));
  }

  @Test
  void multipleStructuredArguments() {
    logger.info(
        "Transaction: {} amount: {}",
        StructuredArguments.v("customer_id", "789"),
        StructuredArguments.v("amount", 99.99));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasBody("Transaction: 789 amount: 99.99")
                .hasAttributesSatisfying(
                    equalTo(AttributeKey.stringKey("customer_id"), "789"),
                    equalTo(AttributeKey.doubleKey("amount"), 99.99)));
  }

  @Test
  void structuredArgumentWithEventName() {
    logger.info("Event occurred: {}", StructuredArguments.v("event.name", "OrderPlaced"));

    testing.waitAndAssertLogRecords(
        logRecord -> logRecord.hasBody("Event occurred: OrderPlaced").hasEventName("OrderPlaced"));
  }

  @Test
  void structuredArgumentsWithTypedValues() {
    long timestamp = System.currentTimeMillis();
    logger.info(
        "User logged in: {} at {} with session: {}",
        StructuredArguments.v("user_id", 12345),
        StructuredArguments.v("timestamp", timestamp),
        StructuredArguments.v("session_active", true));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfying(
                equalTo(AttributeKey.longKey("user_id"), 12345L),
                equalTo(AttributeKey.longKey("timestamp"), timestamp),
                equalTo(AttributeKey.booleanKey("session_active"), true)));
  }
}
