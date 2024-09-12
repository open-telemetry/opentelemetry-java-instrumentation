/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ExceptionAttributes;
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes;
import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.FormattedMessage;
import org.apache.logging.log4j.message.StringMapMessage;
import org.apache.logging.log4j.message.StructuredDataMessage;
import org.assertj.core.api.AssertAccess;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

abstract class AbstractOpenTelemetryAppenderTest {

  static final Logger logger = LogManager.getLogger("TestLogger");

  static Resource resource;
  static InstrumentationScopeInfo instrumentationScopeInfo;

  void executeAfterLogsExecution() {}

  @BeforeAll
  static void setupAll() {
    resource = Resource.getDefault();
    instrumentationScopeInfo = InstrumentationScopeInfo.create("TestLogger");
  }

  static void generalBeforeEachSetup() {
    ThreadContext.clearAll();
  }

  @AfterAll
  static void cleanupAll() {
    // This is to make sure that other test classes don't have issues with the logger provider set
    OpenTelemetryAppender.install(null);
  }

  protected abstract InstrumentationExtension getTesting();

  @Test
  void initializeWithBuilder() {
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(getTesting().getOpenTelemetry())
            .build();
    appender.start();

    appender.append(
        Log4jLogEvent.newBuilder()
            .setMessage(new FormattedMessage("log message 1", (Object) null))
            .build());

    executeAfterLogsExecution();

    getTesting().waitAndAssertLogRecords(logRecord -> logRecord.hasBody("log message 1"));
  }

  @Test
  void logNoSpan() {
    logger.info("log message 1");

    executeAfterLogsExecution();

    getTesting()
        .waitAndAssertLogRecords(
            logRecord ->
                logRecord
                    .hasResource(resource)
                    .hasInstrumentationScope(instrumentationScopeInfo)
                    .hasBody("log message 1")
                    .hasAttributesSatisfyingExactly(
                        equalTo(
                            ThreadIncubatingAttributes.THREAD_NAME,
                            Thread.currentThread().getName()),
                        equalTo(
                            ThreadIncubatingAttributes.THREAD_ID, Thread.currentThread().getId())));
  }

  @Test
  void logWithSpanInvalid() {
    logger.info("log message");

    executeAfterLogsExecution();

    getTesting()
        .waitAndAssertLogRecords(logRecord -> logRecord.hasSpanContext(SpanContext.getInvalid()));
  }

  @Test
  void logWithExtras() {
    Instant start = Instant.now();
    logger.info("log message 1", new IllegalStateException("Error!"));

    executeAfterLogsExecution();

    getTesting()
        .waitAndAssertLogRecords(
            logRecord -> {
              logRecord
                  .hasResource(resource)
                  .hasInstrumentationScope(instrumentationScopeInfo)
                  .hasBody("log message 1")
                  .hasSeverity(Severity.INFO)
                  .hasSeverityText("INFO")
                  .hasAttributesSatisfyingExactly(
                      equalTo(
                          ThreadIncubatingAttributes.THREAD_NAME, Thread.currentThread().getName()),
                      equalTo(ThreadIncubatingAttributes.THREAD_ID, Thread.currentThread().getId()),
                      equalTo(
                          ExceptionAttributes.EXCEPTION_TYPE,
                          IllegalStateException.class.getName()),
                      equalTo(ExceptionAttributes.EXCEPTION_MESSAGE, "Error!"),
                      satisfies(
                          ExceptionAttributes.EXCEPTION_STACKTRACE,
                          v -> v.contains("logWithExtras")));

              LogRecordData logRecordData = AssertAccess.getActual(logRecord);
              assertThat(logRecordData.getTimestampEpochNanos())
                  .isGreaterThanOrEqualTo(MILLISECONDS.toNanos(start.toEpochMilli()))
                  .isLessThanOrEqualTo(MILLISECONDS.toNanos(Instant.now().toEpochMilli()));
            });
  }

  @Test
  void logContextData() {
    ThreadContext.put("key1", "val1");
    ThreadContext.put("key2", "val2");
    try {
      logger.info("log message 1");
    } finally {
      ThreadContext.clearMap();
    }

    executeAfterLogsExecution();

    getTesting()
        .waitAndAssertLogRecords(
            logRecord ->
                logRecord
                    .hasResource(resource)
                    .hasInstrumentationScope(instrumentationScopeInfo)
                    .hasBody("log message 1")
                    .hasAttributesSatisfyingExactly(
                        equalTo(
                            ThreadIncubatingAttributes.THREAD_NAME,
                            Thread.currentThread().getName()),
                        equalTo(
                            ThreadIncubatingAttributes.THREAD_ID, Thread.currentThread().getId()),
                        equalTo(stringKey("key1"), "val1"),
                        equalTo(stringKey("key2"), "val2")));
  }

  @Test
  void logStringMapMessage() {
    StringMapMessage message = new StringMapMessage();
    message.put("key1", "val1");
    message.put("key2", "val2");
    logger.info(message);

    executeAfterLogsExecution();

    getTesting()
        .waitAndAssertLogRecords(
            logRecord ->
                logRecord
                    .hasResource(resource)
                    .hasInstrumentationScope(instrumentationScopeInfo)
                    .hasAttributesSatisfyingExactly(
                        equalTo(
                            ThreadIncubatingAttributes.THREAD_NAME,
                            Thread.currentThread().getName()),
                        equalTo(
                            ThreadIncubatingAttributes.THREAD_ID, Thread.currentThread().getId()),
                        equalTo(stringKey("log4j.map_message.key1"), "val1"),
                        equalTo(stringKey("log4j.map_message.key2"), "val2")));
  }

  @Test
  void logStringMapMessageWithSpecialAttribute() {
    StringMapMessage message = new StringMapMessage();
    message.put("key1", "val1");
    message.put("message", "val2");
    logger.info(message);

    executeAfterLogsExecution();

    getTesting()
        .waitAndAssertLogRecords(
            logRecord ->
                logRecord
                    .hasResource(resource)
                    .hasInstrumentationScope(instrumentationScopeInfo)
                    .hasBody("val2")
                    .hasAttributesSatisfyingExactly(
                        equalTo(
                            ThreadIncubatingAttributes.THREAD_NAME,
                            Thread.currentThread().getName()),
                        equalTo(
                            ThreadIncubatingAttributes.THREAD_ID, Thread.currentThread().getId()),
                        equalTo(stringKey("log4j.map_message.key1"), "val1")));
  }

  @Test
  void testCaptureMarkerAttribute() {
    String markerName = "aMarker";
    Marker marker = MarkerManager.getMarker(markerName);

    logger.info(marker, "Message");

    executeAfterLogsExecution();

    getTesting()
        .waitAndAssertLogRecords(
            logRecord ->
                logRecord.hasAttributesSatisfying(equalTo(stringKey("log4j.marker"), markerName)));
  }

  @Test
  void logStructuredDataMessage() {
    StructuredDataMessage message = new StructuredDataMessage("an id", "a message", "a type");
    message.put("key1", "val1");
    message.put("key2", "val2");
    logger.info(message);

    executeAfterLogsExecution();

    getTesting()
        .waitAndAssertLogRecords(
            logRecord ->
                logRecord
                    .hasResource(resource)
                    .hasInstrumentationScope(instrumentationScopeInfo)
                    .hasBody("a message")
                    .hasAttributesSatisfyingExactly(
                        equalTo(
                            ThreadIncubatingAttributes.THREAD_NAME,
                            Thread.currentThread().getName()),
                        equalTo(
                            ThreadIncubatingAttributes.THREAD_ID, Thread.currentThread().getId()),
                        equalTo(stringKey("log4j.map_message.key1"), "val1"),
                        equalTo(stringKey("log4j.map_message.key2"), "val2")));
  }
}
