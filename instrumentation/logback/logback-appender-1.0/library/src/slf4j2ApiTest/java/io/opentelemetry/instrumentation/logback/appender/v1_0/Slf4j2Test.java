/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.GlobalLoggerProvider;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

public class Slf4j2Test {
  private static final Logger logger = LoggerFactory.getLogger("TestLogger");

  private static InMemoryLogRecordExporter logRecordExporter;
  private static Resource resource;
  private static InstrumentationScopeInfo instrumentationScopeInfo;

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

    GlobalLoggerProvider.resetForTest();
    GlobalLoggerProvider.set(loggerProvider);
  }

  @BeforeEach
  void setup() {
    logRecordExporter.reset();
  }

  @Test
  void keyValue() {
    logger.atInfo().setMessage("log message 1").addKeyValue("key", "value").log();

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogItems();
    assertThat(logDataList).hasSize(1);
    LogRecordData logData = logDataList.get(0);
    assertThat(logData.getResource()).isEqualTo(resource);
    assertThat(logData.getInstrumentationScopeInfo()).isEqualTo(instrumentationScopeInfo);
    assertThat(logData.getBody().asString()).isEqualTo("log message 1");
    assertThat(logData.getAttributes().size()).isEqualTo(5); // 4 code attributes + 1 key value pair
    assertThat(logData.getAttributes())
        .hasEntrySatisfying(
            AttributeKey.stringKey("key"), value -> assertThat(value).isEqualTo("value"));
  }

  @Test
  void multipleMarkers() {
    String markerName1 = "aMarker1";
    String markerName2 = "aMarker2";
    logger
        .atInfo()
        .setMessage("log message 1")
        .addMarker(MarkerFactory.getMarker(markerName1))
        .addMarker(MarkerFactory.getMarker(markerName2))
        .log();

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogItems();
    assertThat(logDataList).hasSize(1);
    LogRecordData logData = logDataList.get(0);
    assertThat(logData.getResource()).isEqualTo(resource);
    assertThat(logData.getInstrumentationScopeInfo()).isEqualTo(instrumentationScopeInfo);
    assertThat(logData.getBody().asString()).isEqualTo("log message 1");
    assertThat(logData.getAttributes().size()).isEqualTo(6); // 4 code attributes + 2 markers
    assertThat(logData.getAttributes())
        .hasEntrySatisfying(
            AttributeKey.stringKey("logback.marker.1"),
            value -> assertThat(value).isEqualTo(markerName1));
    assertThat(logData.getAttributes())
        .hasEntrySatisfying(
            AttributeKey.stringKey("logback.marker.2"),
            value -> assertThat(value).isEqualTo(markerName2));
  }
}
