/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import java.util.List;
import org.apache.logging.log4j.message.StringMapMessage;
import org.apache.logging.log4j.message.StructuredDataMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LogReplayOpenTelemetryAppenderTest extends AbstractOpenTelemetryAppenderTest {

  @BeforeEach
  void setup() {
    generalBeforeEachSetup();
  }

  @AfterEach
  void resetOpenTelemetry() {
    OpenTelemetryAppender.install(null);
  }

  @Override
  void executeAfterLogsExecution() {
    OpenTelemetryAppender.install(openTelemetry);
  }

  @Test
  void twoLogs() {
    logger.info("log message 1");
    logger.info(
        "log message 2"); // Won't be instrumented because cache size is 1 (see log4j2.xml file)

    OpenTelemetryAppender.install(openTelemetry);

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList).hasSize(1);
    OpenTelemetryAssertions.assertThat(logDataList.get(0))
        .hasResource(resource)
        .hasInstrumentationScope(instrumentationScopeInfo)
        .hasBody("log message 1");
  }

  @Test
  void twoLogsStringMapMessage() {
    StringMapMessage message = new StringMapMessage();
    message.put("key1", "val1");
    message.put("key2", "val2");

    logger.info(message);

    StringMapMessage message2 = new StringMapMessage();
    message2.put("key1-2", "val1-2");
    message2.put("key2-2", "val2-2");

    logger.info(message2); // Won't be instrumented because cache size is 1 (see log4j2.xml file)

    OpenTelemetryAppender.install(openTelemetry);

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList).hasSize(1);
    OpenTelemetryAssertions.assertThat(logDataList.get(0))
        .hasResource(resource)
        .hasInstrumentationScope(instrumentationScopeInfo)
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("log4j.map_message.key1"), "val1"),
            equalTo(stringKey("log4j.map_message.key2"), "val2"));
  }

  @Test
  void twoLogsStructuredDataMessage() {
    StructuredDataMessage message = new StructuredDataMessage("an id", "a message", "a type");
    message.put("key1", "val1");
    message.put("key2", "val2");
    logger.info(message);

    StructuredDataMessage message2 =
        new StructuredDataMessage("an id 2", "a message 2", "a type 2");
    message.put("key1-2", "val1-2");
    message.put("key2-2", "val2-2");
    logger.info(message2); // Won't be instrumented because cache size is 1 (see log4j2.xml file)

    OpenTelemetryAppender.install(openTelemetry);

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList).hasSize(1);
    OpenTelemetryAssertions.assertThat(logDataList.get(0))
        .hasResource(resource)
        .hasInstrumentationScope(instrumentationScopeInfo)
        .hasBody("a message")
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("log4j.map_message.key1"), "val1"),
            equalTo(stringKey("log4j.map_message.key2"), "val2"));
  }
}
