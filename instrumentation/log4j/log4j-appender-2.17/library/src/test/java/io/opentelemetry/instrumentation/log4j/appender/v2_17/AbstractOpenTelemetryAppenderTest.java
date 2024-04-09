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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.semconv.ExceptionAttributes;
import java.time.Instant;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.FormattedMessage;
import org.apache.logging.log4j.message.StringMapMessage;
import org.apache.logging.log4j.message.StructuredDataMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

abstract class AbstractOpenTelemetryAppenderTest {

  static final Logger logger = LogManager.getLogger("TestLogger");

  static InMemoryLogRecordExporter logRecordExporter;
  static Resource resource;
  static InstrumentationScopeInfo instrumentationScopeInfo;
  static OpenTelemetry openTelemetry;

  void executeAfterLogsExecution() {}

  @BeforeAll
  static void setupAll() {
    logRecordExporter = InMemoryLogRecordExporter.create();
    resource = Resource.getDefault();
    instrumentationScopeInfo = InstrumentationScopeInfo.create("TestLogger");

    SdkLoggerProvider loggerProvider =
        SdkLoggerProvider.builder()
            .setResource(resource)
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(logRecordExporter))
            .build();

    openTelemetry = OpenTelemetrySdk.builder().setLoggerProvider(loggerProvider).build();
  }

  static void generalBeforeEachSetup() {
    logRecordExporter.reset();
    ThreadContext.clearAll();
  }

  @AfterAll
  static void cleanupAll() {
    // This is to make sure that other test classes don't have issues with the logger provider set
    OpenTelemetryAppender.install(null);
  }

  @Test
  void initializeWithBuilder() {
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(openTelemetry)
            .build();
    appender.start();

    appender.append(
        Log4jLogEvent.newBuilder()
            .setMessage(new FormattedMessage("log message 1", (Object) null))
            .build());

    executeAfterLogsExecution();

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList)
        .satisfiesExactly(logRecordData -> assertThat(logDataList.get(0)).hasBody("log message 1"));
  }

  @Test
  void logNoSpan() {
    logger.info("log message 1");

    executeAfterLogsExecution();

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList).hasSize(1);
    assertThat(logDataList.get(0))
        .hasResource(resource)
        .hasInstrumentationScope(instrumentationScopeInfo)
        .hasBody("log message 1")
        .hasAttributes(Attributes.empty());
  }

  @Test
  void logWithSpanInvalid() {
    logger.info("log message");

    executeAfterLogsExecution();

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList).hasSize(1);
    assertThat(logDataList.get(0).getSpanContext()).isEqualTo(SpanContext.getInvalid());
  }

  @Test
  void logWithExtras() {
    Instant start = Instant.now();
    logger.info("log message 1", new IllegalStateException("Error!"));

    executeAfterLogsExecution();

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList).hasSize(1);
    assertThat(logDataList.get(0))
        .hasResource(resource)
        .hasInstrumentationScope(instrumentationScopeInfo)
        .hasBody("log message 1")
        .hasSeverity(Severity.INFO)
        .hasSeverityText("INFO")
        .hasAttributesSatisfyingExactly(
            equalTo(ExceptionAttributes.EXCEPTION_TYPE, IllegalStateException.class.getName()),
            equalTo(ExceptionAttributes.EXCEPTION_MESSAGE, "Error!"),
            satisfies(ExceptionAttributes.EXCEPTION_STACKTRACE, v -> v.contains("logWithExtras")));

    assertThat(logDataList.get(0).getTimestampEpochNanos())
        .isGreaterThanOrEqualTo(MILLISECONDS.toNanos(start.toEpochMilli()))
        .isLessThanOrEqualTo(MILLISECONDS.toNanos(Instant.now().toEpochMilli()));
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

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList).hasSize(1);
    assertThat(logDataList.get(0))
        .hasResource(resource)
        .hasInstrumentationScope(instrumentationScopeInfo)
        .hasBody("log message 1")
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("key1"), "val1"), equalTo(stringKey("key2"), "val2"));
  }

  @Test
  void logStringMapMessage() {
    StringMapMessage message = new StringMapMessage();
    message.put("key1", "val1");
    message.put("key2", "val2");
    logger.info(message);

    executeAfterLogsExecution();

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList).hasSize(1);
    assertThat(logDataList.get(0))
        .hasResource(resource)
        .hasInstrumentationScope(instrumentationScopeInfo)
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("log4j.map_message.key1"), "val1"),
            equalTo(stringKey("log4j.map_message.key2"), "val2"));
  }

  @Test
  void logStringMapMessageWithSpecialAttribute() {
    StringMapMessage message = new StringMapMessage();
    message.put("key1", "val1");
    message.put("message", "val2");
    logger.info(message);

    executeAfterLogsExecution();

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList).hasSize(1);
    assertThat(logDataList.get(0))
        .hasResource(resource)
        .hasInstrumentationScope(instrumentationScopeInfo)
        .hasBody("val2")
        .hasAttributesSatisfyingExactly(equalTo(stringKey("log4j.map_message.key1"), "val1"));
  }

  @Test
  void testCaptureMarkerAttribute() {
    String markerName = "aMarker";
    Marker marker = MarkerManager.getMarker(markerName);

    logger.info(marker, "Message");

    executeAfterLogsExecution();

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    LogRecordData logData = logDataList.get(0);
    assertThat(logData.getAttributes().get(stringKey("log4j.marker"))).isEqualTo(markerName);
  }

  @Test
  void logStructuredDataMessage() {
    StructuredDataMessage message = new StructuredDataMessage("an id", "a message", "a type");
    message.put("key1", "val1");
    message.put("key2", "val2");
    logger.info(message);

    executeAfterLogsExecution();

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList).hasSize(1);
    assertThat(logDataList.get(0))
        .hasResource(resource)
        .hasInstrumentationScope(instrumentationScopeInfo)
        .hasBody("a message")
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("log4j.map_message.key1"), "val1"),
            equalTo(stringKey("log4j.map_message.key2"), "val2"));
  }
}
