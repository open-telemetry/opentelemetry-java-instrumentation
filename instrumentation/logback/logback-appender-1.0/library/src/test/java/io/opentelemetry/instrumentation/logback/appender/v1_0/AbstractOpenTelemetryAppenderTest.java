/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.ContextBase;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.semconv.ExceptionAttributes;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

abstract class AbstractOpenTelemetryAppenderTest {

  static final Logger logger = LoggerFactory.getLogger("TestLogger");

  static InMemoryLogRecordExporter logRecordExporter;
  static Resource resource;
  static InstrumentationScopeInfo instrumentationScopeInfo;

  static OpenTelemetrySdk openTelemetrySdk;

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
    openTelemetrySdk = OpenTelemetrySdk.builder().setLoggerProvider(loggerProvider).build();
    // by default LoggerContext contains HOSTNAME property we clear it to start with empty context
    resetLoggerContext();
  }

  static void resetLoggerContext() {
    try {
      LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
      Field field = ContextBase.class.getDeclaredField("propertyMap");
      field.setAccessible(true);
      Map<?, ?> map = (Map<?, ?>) field.get(loggerContext);
      map.clear();

      Method method;
      try {
        method = LoggerContext.class.getDeclaredMethod("syncRemoteView");
      } catch (NoSuchMethodException noSuchMethodException) {
        method = LoggerContext.class.getDeclaredMethod("updateLoggerContextVO");
      }
      method.setAccessible(true);
      method.invoke(loggerContext);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to reset logger context", exception);
    }
  }

  static void generalBeforeEachSetup() {
    logRecordExporter.reset();
  }

  @Test
  void logNoSpan() {
    logger.info("log message 1");

    executeAfterLogsExecution();

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList).hasSize(1);
    LogRecordData logData = logDataList.get(0);

    assertThat(logData)
        .hasResource(resource)
        .hasInstrumentationScope(instrumentationScopeInfo)
        .hasBody("log message 1")
        .hasTotalAttributeCount(4);
  }

  @Test
  void logWithExtras() {
    Instant start = Instant.now();
    String markerName = "aMarker";
    Marker marker = MarkerFactory.getMarker(markerName);
    logger.info(marker, "log message 1", new IllegalStateException("Error!"));

    executeAfterLogsExecution();

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList).hasSize(1);
    LogRecordData logData = logDataList.get(0);
    assertThat(logData.getResource()).isEqualTo(resource);
    assertThat(logData.getInstrumentationScopeInfo()).isEqualTo(instrumentationScopeInfo);
    assertThat(logData.getBody().asString()).isEqualTo("log message 1");
    assertThat(logData.getTimestampEpochNanos())
        .isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(start.toEpochMilli()))
        .isLessThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(Instant.now().toEpochMilli()));
    assertThat(logData.getSeverity()).isEqualTo(Severity.INFO);
    assertThat(logData.getSeverityText()).isEqualTo("INFO");
    assertThat(logData.getAttributes().size())
        .isEqualTo(3 + 4 + 1); // 3 exception attributes, 4 code attributes, 1 marker attribute
    assertThat(logData.getAttributes().get(ExceptionAttributes.EXCEPTION_TYPE))
        .isEqualTo(IllegalStateException.class.getName());
    assertThat(logData.getAttributes().get(ExceptionAttributes.EXCEPTION_MESSAGE))
        .isEqualTo("Error!");
    assertThat(logData.getAttributes().get(ExceptionAttributes.EXCEPTION_STACKTRACE))
        .contains("logWithExtras");

    String file = logData.getAttributes().get(CodeIncubatingAttributes.CODE_FILEPATH);
    assertThat(file).isEqualTo(AbstractOpenTelemetryAppenderTest.class.getSimpleName() + ".java");

    String codeClass = logData.getAttributes().get(CodeIncubatingAttributes.CODE_NAMESPACE);
    assertThat(codeClass).isEqualTo(AbstractOpenTelemetryAppenderTest.class.getName());

    String method = logData.getAttributes().get(CodeIncubatingAttributes.CODE_FUNCTION);
    assertThat(method).isEqualTo("logWithExtras");

    Long lineNumber = logData.getAttributes().get(CodeIncubatingAttributes.CODE_LINENO);
    assertThat(lineNumber).isGreaterThan(1);

    List<String> logMarker =
        logData.getAttributes().get(AttributeKey.stringArrayKey("logback.marker"));
    assertThat(logMarker).isEqualTo(Arrays.asList(markerName));
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

    executeAfterLogsExecution();

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList).hasSize(1);
    LogRecordData logData = logDataList.get(0);
    assertThat(logData.getResource()).isEqualTo(resource);
    assertThat(logData.getInstrumentationScopeInfo()).isEqualTo(instrumentationScopeInfo);
    assertThat(logData.getBody().asString()).isEqualTo("log message 1");
    assertThat(logData.getAttributes().size()).isEqualTo(2 + 4); // 4 code attributes
    assertThat(logData.getAttributes().get(AttributeKey.stringKey("key1"))).isEqualTo("val1");
    assertThat(logData.getAttributes().get(AttributeKey.stringKey("key2"))).isEqualTo("val2");
  }

  @Test
  void logLoggerContext() {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    loggerContext.putProperty("test-property", "test-value");
    try {
      logger.info("log message 1");
      executeAfterLogsExecution();
    } finally {
      resetLoggerContext();
    }

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList).hasSize(1);
    LogRecordData logData = logDataList.get(0);
    assertThat(logData.getResource()).isEqualTo(resource);
    assertThat(logData.getInstrumentationScopeInfo()).isEqualTo(instrumentationScopeInfo);
    assertThat(logData.getBody().asString()).isEqualTo("log message 1");
    assertThat(logData.getAttributes().size()).isEqualTo(1 + 4); // 4 code attributes
    assertThat(logData.getAttributes().get(AttributeKey.stringKey("test-property")))
        .isEqualTo("test-value");
  }
}
