/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static io.opentelemetry.instrumentation.log4j.appender.v2_17.AbstractLog4j2Test.mapMessageKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_ID;
import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_NAME;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.List;
import org.apache.logging.log4j.message.StringMapMessage;
import org.apache.logging.log4j.message.StructuredDataMessage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class LogReplayOpenTelemetryAppenderTest extends AbstractOpenTelemetryAppenderTest {

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @BeforeEach
  void setup() {
    generalBeforeEachSetup();
  }

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  void executeAfterLogsExecution() {
    OpenTelemetryAppender.install(testing.getOpenTelemetry());
    cleanup.deferCleanup(OpenTelemetryAppender::resetForTest);
  }

  private static boolean isAsyncLogger() {
    return logger.getClass().getName().contains("AsyncLogger");
  }

  @Test
  void twoLogs() {
    // with async logger OpenTelemetryAppender.install may be called before second log message is
    // captured, so we get 2 log records instead of the expected 1
    Assumptions.assumeFalse(isAsyncLogger());

    logger.info("log message 1");
    logger.info(
        "log message 2"); // Won't be instrumented because cache size is 1 (see log4j2.xml file)

    OpenTelemetryAppender.install(testing.getOpenTelemetry());
    cleanup.deferCleanup(OpenTelemetryAppender::resetForTest);

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasResource(RESOURCE)
                .hasInstrumentationScope(INSTRUMENTATION_SCOPE_INFO)
                .hasBody("log message 1"));
  }

  @Test
  void twoLogsStringMapMessage() {
    // with async logger OpenTelemetryAppender.install may be called before second log message is
    // captured, so we get 2 log records instead of the expected 1
    Assumptions.assumeFalse(isAsyncLogger());

    StringMapMessage message = new StringMapMessage();
    message.put("key1", "val1");
    message.put("key2", "val2");

    logger.info(message);

    StringMapMessage message2 = new StringMapMessage();
    message2.put("key1-2", "val1-2");
    message2.put("key2-2", "val2-2");

    logger.info(message2); // Won't be instrumented because cache size is 1 (see log4j2.xml file)

    OpenTelemetryAppender.install(testing.getOpenTelemetry());
    cleanup.deferCleanup(OpenTelemetryAppender::resetForTest);

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasResource(RESOURCE)
                .hasInstrumentationScope(INSTRUMENTATION_SCOPE_INFO)
                .hasAttributesSatisfyingExactly(
                    addLocationAttributes(
                        "twoLogsStringMapMessage",
                        equalTo(THREAD_NAME, Thread.currentThread().getName()),
                        equalTo(THREAD_ID, Thread.currentThread().getId()),
                        equalTo(mapMessageKey("key1"), "val1"),
                        equalTo(mapMessageKey("key2"), "val2"))));
  }

  @Test
  void twoLogsStructuredDataMessage() {
    // with async logger OpenTelemetryAppender.install may be called before second log message is
    // captured, so we get 2 log records instead of the expected 1
    Assumptions.assumeFalse(isAsyncLogger());

    StructuredDataMessage message = new StructuredDataMessage("an id", "a message", "a type");
    message.put("key1", "val1");
    message.put("key2", "val2");
    logger.info(message);

    StructuredDataMessage message2 =
        new StructuredDataMessage("an id 2", "a message 2", "a type 2");
    message2.put("key1-2", "val1-2");
    message2.put("key2-2", "val2-2");
    logger.info(message2); // Won't be instrumented because cache size is 1 (see log4j2.xml file)

    OpenTelemetryAppender.install(testing.getOpenTelemetry());
    cleanup.deferCleanup(OpenTelemetryAppender::resetForTest);

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasResource(RESOURCE)
                .hasInstrumentationScope(INSTRUMENTATION_SCOPE_INFO)
                .hasBody("a message")
                .hasAttributesSatisfyingExactly(
                    addLocationAttributes(
                        "twoLogsStructuredDataMessage",
                        equalTo(THREAD_NAME, Thread.currentThread().getName()),
                        equalTo(THREAD_ID, Thread.currentThread().getId()),
                        equalTo(mapMessageKey("key1"), "val1"),
                        equalTo(mapMessageKey("key2"), "val2"))));
  }

  private static List<AttributeAssertion> addLocationAttributes(
      String methodName, AttributeAssertion... assertions) {
    return addLocationAttributes(LogReplayOpenTelemetryAppenderTest.class, methodName, assertions);
  }
}
