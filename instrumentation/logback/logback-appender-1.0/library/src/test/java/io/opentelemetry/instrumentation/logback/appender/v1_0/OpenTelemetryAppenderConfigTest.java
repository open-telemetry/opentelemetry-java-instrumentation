/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

class OpenTelemetryAppenderConfigTest {

  private static final Logger logger = LoggerFactory.getLogger("TestLogger");

  private static InMemoryLogRecordExporter logRecordExporter;
  private static Resource resource;
  private static InstrumentationScopeInfo instrumentationScopeInfo;

  @BeforeAll
  static void setupAll() {
    logRecordExporter = InMemoryLogRecordExporter.create();
    resource = Resource.getDefault();
    instrumentationScopeInfo = InstrumentationScopeInfo.create("TestLogger");

    SdkLoggerProvider logEmitterProvider =
        SdkLoggerProvider.builder()
            .setResource(resource)
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(logRecordExporter))
            .build();

    OpenTelemetryAppender.resetSdkLogEmitterProviderForTest();
    OpenTelemetryAppender.setSdkLogEmitterProvider(logEmitterProvider);
  }

  @BeforeEach
  void setup() {
    logRecordExporter.reset();
  }

  @Test
  void logNoSpan() {
    logger.info("log message 1");

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogItems();
    assertThat(logDataList).hasSize(1);
    LogRecordData logData = logDataList.get(0);
    assertThat(logData.getResource()).isEqualTo(resource);
    assertThat(logData.getInstrumentationScopeInfo()).isEqualTo(instrumentationScopeInfo);
    assertThat(logData.getBody().asString()).isEqualTo("log message 1");
    assertThat(logData.getAttributes().size()).isEqualTo(4); // 4 code attributes
  }

  @Test
  void logWithSpan() {
    Span span1 = runWithSpan("span1", () -> logger.info("log message 1"));

    logger.info("log message 2");

    Span span2 = runWithSpan("span2", () -> logger.info("log message 3"));

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogItems();
    assertThat(logDataList).hasSize(3);
    assertThat(logDataList.get(0).getSpanContext()).isEqualTo(span1.getSpanContext());
    assertThat(logDataList.get(1).getSpanContext()).isEqualTo(SpanContext.getInvalid());
    assertThat(logDataList.get(2).getSpanContext()).isEqualTo(span2.getSpanContext());
  }

  private static Span runWithSpan(String spanName, Runnable runnable) {
    Span span = SdkTracerProvider.builder().build().get("tracer").spanBuilder(spanName).startSpan();
    try (Scope ignored = span.makeCurrent()) {
      runnable.run();
    } finally {
      span.end();
    }
    return span;
  }

  @Test
  void logWithExtras() {
    Instant start = Instant.now();
    String markerName = "aMarker";
    Marker marker = MarkerFactory.getMarker(markerName);
    logger.info(marker, "log message 1", new IllegalStateException("Error!"));

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogItems();
    assertThat(logDataList).hasSize(1);
    LogRecordData logData = logDataList.get(0);
    assertThat(logData.getResource()).isEqualTo(resource);
    assertThat(logData.getInstrumentationScopeInfo()).isEqualTo(instrumentationScopeInfo);
    assertThat(logData.getBody().asString()).isEqualTo("log message 1");
    assertThat(logData.getEpochNanos())
        .isGreaterThan(TimeUnit.MILLISECONDS.toNanos(start.toEpochMilli()))
        .isLessThan(TimeUnit.MILLISECONDS.toNanos(Instant.now().toEpochMilli()));
    assertThat(logData.getSeverity()).isEqualTo(Severity.INFO);
    assertThat(logData.getSeverityText()).isEqualTo("INFO");
    assertThat(logData.getAttributes().size())
        .isEqualTo(3 + 4 + 1); // 3 exception attributes, 4 code attributes, 1 marker attribute
    assertThat(logData.getAttributes().get(SemanticAttributes.EXCEPTION_TYPE))
        .isEqualTo(IllegalStateException.class.getName());
    assertThat(logData.getAttributes().get(SemanticAttributes.EXCEPTION_MESSAGE))
        .isEqualTo("Error!");
    assertThat(logData.getAttributes().get(SemanticAttributes.EXCEPTION_STACKTRACE))
        .contains("logWithExtras");

    String file = logData.getAttributes().get(SemanticAttributes.CODE_FILEPATH);
    assertThat(file).isEqualTo("OpenTelemetryAppenderConfigTest.java");

    String codeClass = logData.getAttributes().get(SemanticAttributes.CODE_NAMESPACE);
    assertThat(codeClass)
        .isEqualTo(
            "io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppenderConfigTest");

    String method = logData.getAttributes().get(SemanticAttributes.CODE_FUNCTION);
    assertThat(method).isEqualTo("logWithExtras");

    Long lineNumber = logData.getAttributes().get(SemanticAttributes.CODE_LINENO);
    assertThat(lineNumber).isGreaterThan(1);

    String logMarker = logData.getAttributes().get(AttributeKey.stringKey("logback.marker"));
    assertThat(logMarker).isEqualTo(markerName);
  }

  @Test
  void logContextData() {
    MDC.put("key1", "val1");
    MDC.put("key2", "val2");
    try {
      logger.info("log message 1");
    } finally {
      MDC.clear();
    }

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogItems();
    assertThat(logDataList).hasSize(1);
    LogRecordData logData = logDataList.get(0);
    assertThat(logData.getResource()).isEqualTo(resource);
    assertThat(logData.getInstrumentationScopeInfo()).isEqualTo(instrumentationScopeInfo);
    assertThat(logData.getBody().asString()).isEqualTo("log message 1");
    assertThat(logData.getAttributes().size()).isEqualTo(2 + 4); // 4 code attributes
    AssertionsForClassTypes.assertThat(
            logData.getAttributes().get(AttributeKey.stringKey("logback.mdc.key1")))
        .isEqualTo("val1");
    AssertionsForClassTypes.assertThat(
            logData.getAttributes().get(AttributeKey.stringKey("logback.mdc.key2")))
        .isEqualTo("val2");
  }
}
