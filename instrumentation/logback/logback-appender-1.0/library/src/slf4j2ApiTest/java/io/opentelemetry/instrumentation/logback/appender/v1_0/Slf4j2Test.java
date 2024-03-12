/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.util.Arrays;
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
    OpenTelemetrySdk openTelemetrySdk =
        OpenTelemetrySdk.builder().setLoggerProvider(loggerProvider).build();

    OpenTelemetryAppender.install(openTelemetrySdk);
  }

  @BeforeEach
  void setup() {
    logRecordExporter.reset();
  }

  @Test
  void keyValue() {
    logger
        .atInfo()
        .setMessage("log message 1")
        .addKeyValue("string key", "string value")
        .addKeyValue("boolean key", true)
        .addKeyValue("byte key", (byte) 1)
        .addKeyValue("short key", (short) 2)
        .addKeyValue("int key", 3)
        .addKeyValue("long key", 4L)
        .addKeyValue("float key", 5.0f)
        .addKeyValue("double key", 6.0)
        .log();

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList).hasSize(1);
    LogRecordData logData = logDataList.get(0);
    assertThat(logData.getResource()).isEqualTo(resource);
    assertThat(logData.getInstrumentationScopeInfo()).isEqualTo(instrumentationScopeInfo);
    assertThat(logData.getBody().asString()).isEqualTo("log message 1");
    assertThat(logData.getAttributes().size())
        .isEqualTo(12); // 4 code attributes + 8 key value pairs
    assertThat(logData)
        .hasAttributesSatisfying(
            equalTo(AttributeKey.stringKey("string key"), "string value"),
            equalTo(AttributeKey.booleanKey("boolean key"), true),
            equalTo(AttributeKey.longKey("byte key"), 1),
            equalTo(AttributeKey.longKey("short key"), 2),
            equalTo(AttributeKey.longKey("int key"), 3),
            equalTo(AttributeKey.longKey("long key"), 4),
            equalTo(AttributeKey.doubleKey("float key"), 5.0),
            equalTo(AttributeKey.doubleKey("double key"), 6.0));
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

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList).hasSize(1);
    LogRecordData logData = logDataList.get(0);
    assertThat(logData.getResource()).isEqualTo(resource);
    assertThat(logData.getInstrumentationScopeInfo()).isEqualTo(instrumentationScopeInfo);
    assertThat(logData.getBody().asString()).isEqualTo("log message 1");
    assertThat(logData.getAttributes().size()).isEqualTo(5); // 4 code attributes + 1 marker
    assertThat(logData.getAttributes())
        .hasEntrySatisfying(
            AttributeKey.stringArrayKey("logback.marker"),
            value -> assertThat(value).isEqualTo(Arrays.asList(markerName1, markerName2)));
  }
}
