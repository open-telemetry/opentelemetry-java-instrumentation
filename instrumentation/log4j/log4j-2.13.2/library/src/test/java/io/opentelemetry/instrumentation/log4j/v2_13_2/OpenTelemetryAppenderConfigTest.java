/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.v2_13_2;

import static io.opentelemetry.instrumentation.log4j.v2_13_2.LogEventMapper.ATTR_FQCN;
import static io.opentelemetry.instrumentation.log4j.v2_13_2.LogEventMapper.ATTR_MARKER;
import static io.opentelemetry.instrumentation.log4j.v2_13_2.LogEventMapper.ATTR_NDC;
import static io.opentelemetry.instrumentation.log4j.v2_13_2.LogEventMapper.ATTR_THREAD_ID;
import static io.opentelemetry.instrumentation.log4j.v2_13_2.LogEventMapper.ATTR_THREAD_NAME;
import static io.opentelemetry.instrumentation.log4j.v2_13_2.LogEventMapper.ATTR_THREAD_PRIORITY;
import static io.opentelemetry.instrumentation.log4j.v2_13_2.LogEventMapper.ATTR_THROWABLE_MESSAGE;
import static io.opentelemetry.instrumentation.log4j.v2_13_2.LogEventMapper.ATTR_THROWABLE_NAME;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.logs.SdkLogEmitterProvider;
import io.opentelemetry.sdk.logs.data.LogData;
import io.opentelemetry.sdk.logs.data.Severity;
import io.opentelemetry.sdk.logs.export.InMemoryLogExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenTelemetryAppenderConfigTest {

  private static final Logger logger = LogManager.getLogger("TestLogger");

  private static InMemoryLogExporter logExporter;
  private static Resource resource;
  private static InstrumentationLibraryInfo instrumentationLibraryInfo;

  @BeforeAll
  static void setupAll() {
    logExporter = InMemoryLogExporter.create();
    resource = Resource.getDefault();
    instrumentationLibraryInfo = InstrumentationLibraryInfo.create("TestLogger", null);

    SdkLogEmitterProvider logEmitterProvider =
        SdkLogEmitterProvider.builder()
            .setResource(resource)
            .addLogProcessor(SimpleLogProcessor.create(logExporter))
            .build();
    OpenTelemetryLog4j.initialize(logEmitterProvider);
  }

  @AfterAll
  static void afterAll() {
    OpenTelemetryLog4j.resetForTest();
  }

  @BeforeEach
  void setup() {
    logExporter.reset();
    ThreadContext.clearAll();
  }

  @Test
  void logNoSpan() {
    logger.info("log message 1");

    List<LogData> logDataList = logExporter.getFinishedLogItems();
    assertThat(logDataList).hasSize(1);
    LogData logData = logDataList.get(0);
    assertThat(logData.getResource()).isEqualTo(resource);
    assertThat(logData.getInstrumentationLibraryInfo()).isEqualTo(instrumentationLibraryInfo);
    assertThat(logData.getBody().asString()).isEqualTo("log message 1");
    assertThat(logData.getAttributes().size()).isGreaterThan(0);
  }

  @Test
  void logWithSpan() {
    Span span1 = runWithSpan("span1", () -> logger.info("log message 1"));

    logger.info("log message 2");

    Span span2 = runWithSpan("span2", () -> logger.info("log message 3"));

    List<LogData> logDataList = logExporter.getFinishedLogItems();
    assertThat(logDataList).hasSize(3);
    assertThat(logDataList.get(0).getSpanContext()).isEqualTo(span1.getSpanContext());
    assertThat(logDataList.get(1).getSpanContext()).isEqualTo(SpanContext.getInvalid());
    assertThat(logDataList.get(2).getSpanContext()).isEqualTo(span2.getSpanContext());
  }

  private static Span runWithSpan(String spanName, Runnable runnable) {
    Span span = SdkTracerProvider.builder().build().get("tracer").spanBuilder(spanName).startSpan();
    try (Scope unused = span.makeCurrent()) {
      runnable.run();
    } finally {
      span.end();
    }
    return span;
  }

  @Test
  void logWithExtras() {
    ThreadContext.put("mdc-key", "mdc-value");
    ThreadContext.push("ndc-value1");
    ThreadContext.push("ndc-value2");
    logger.info(
        MarkerManager.getMarker("my-marker"), "log message 1", new IllegalStateException("Error!"));

    List<LogData> logDataList = logExporter.getFinishedLogItems();
    assertThat(logDataList).hasSize(1);
    LogData logData = logDataList.get(0);
    assertThat(logData.getResource()).isEqualTo(resource);
    assertThat(logData.getInstrumentationLibraryInfo()).isEqualTo(instrumentationLibraryInfo);
    assertThat(logData.getBody().asString()).isEqualTo("log message 1");
    assertThat(logData.getEpochNanos())
        .isGreaterThan(TimeUnit.MILLISECONDS.toNanos(Instant.now().toEpochMilli() - 1000));
    assertThat(logData.getSeverity()).isEqualTo(Severity.INFO);
    assertThat(logData.getSeverityText()).isEqualTo("INFO");
    Attributes attributes = logData.getAttributes();
    assertThat(attributes.get(ATTR_FQCN)).isEqualTo(AbstractLogger.class.getName());
    assertThat(attributes.get(ATTR_THREAD_NAME)).isNotEmpty();
    assertThat(attributes.get(ATTR_THREAD_ID)).isGreaterThan(0);
    assertThat(attributes.get(ATTR_THREAD_PRIORITY)).isGreaterThan(0);
    assertThat(attributes.get(ATTR_THROWABLE_NAME))
        .isEqualTo(IllegalStateException.class.getName());
    assertThat(attributes.get(ATTR_THROWABLE_MESSAGE)).isEqualTo("Error!");
    assertThat(attributes.get(ATTR_MARKER)).isEqualTo("my-marker");
    assertThat(attributes.get(ATTR_NDC)).isEqualTo(Arrays.asList("ndc-value1", "ndc-value2"));
    assertThat(attributes.get(AttributeKey.stringKey("mdc-key"))).isEqualTo("mdc-value");
  }
}
