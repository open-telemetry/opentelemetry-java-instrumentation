/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LogReplayOpenTelemetryAppenderTest extends AbstractOpenTelemetryAppenderTest {

  @BeforeEach
  void setup() {
    generalBeforeEachSetup();
    OpenTelemetryAppender.install(OpenTelemetry.noop());
  }

  @Override
  void executeAfterLogsExecution() {
    OpenTelemetryAppender.install(openTelemetrySdk);
  }

  @Test
  void twoLogs() {
    logger.info("log message 1");
    logger.info(
        "log message 2"); // Won't be instrumented because cache size is 1 (see logback-test.xml
    // file)

    OpenTelemetryAppender.install(openTelemetrySdk);

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList).hasSize(1);
    LogRecordData logData = logDataList.get(0);
    OpenTelemetryAssertions.assertThat(logData)
        .hasResource(resource)
        .hasInstrumentationScope(instrumentationScopeInfo)
        .hasBody("log message 1")
        .hasTotalAttributeCount(4);
  }
}
