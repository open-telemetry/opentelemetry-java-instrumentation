/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.FormattedMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OpenTelemetryAppenderTest extends AbstractOpenTelemetryConfigTest {

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

    GlobalOpenTelemetry.resetForTest();
    openTelemetry = OpenTelemetrySdk.builder().setLoggerProvider(loggerProvider).build();
    setOpenTelemetry(openTelemetry);
  }

  private static void setOpenTelemetry(OpenTelemetry openTelemetry) {
    Configuration config = ((LoggerContext) LogManager.getContext(false)).getConfiguration();
    config.getAppenders().values().stream()
        .filter(a -> a instanceof OpenTelemetryAppender)
        .forEach(a -> ((OpenTelemetryAppender) a).setOpenTelemetry(openTelemetry));
  }

  @AfterAll
  static void cleanupAll() {
    // This is to make sure that other test classes don't have issues with the logger provider set
    setOpenTelemetry(null);
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

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList)
        .satisfiesExactly(logRecordData -> assertThat(logDataList.get(0)).hasBody("log message 1"));
  }
}
