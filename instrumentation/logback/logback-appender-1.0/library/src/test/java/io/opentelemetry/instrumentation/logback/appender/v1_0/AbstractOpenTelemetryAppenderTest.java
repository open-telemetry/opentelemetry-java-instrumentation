/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.ContextBase;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AssertAccess;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

abstract class AbstractOpenTelemetryAppenderTest {

  static final Logger logger = LoggerFactory.getLogger("TestLogger");

  static Resource resource;
  static InstrumentationScopeInfo instrumentationScopeInfo;

  void executeAfterLogsExecution() {}

  @BeforeAll
  static void setupAll() {
    resource = Resource.getDefault();
    instrumentationScopeInfo = InstrumentationScopeInfo.create("TestLogger");
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

  protected abstract InstrumentationExtension getTesting();

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
                    .hasTotalAttributeCount(4));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void logWithExtras() {
    Instant start = Instant.now();
    String markerName = "aMarker";
    Marker marker = MarkerFactory.getMarker(markerName);
    logger.info(marker, "log message 1", new IllegalStateException("Error!"));

    executeAfterLogsExecution();

    Instant now = Instant.now();
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
                      equalTo(EXCEPTION_TYPE, IllegalStateException.class.getName()),
                      equalTo(EXCEPTION_MESSAGE, "Error!"),
                      satisfies(
                          EXCEPTION_STACKTRACE, stackTrace -> stackTrace.contains("logWithExtras")),
                      equalTo(
                          CodeIncubatingAttributes.CODE_FILEPATH,
                          AbstractOpenTelemetryAppenderTest.class.getSimpleName() + ".java"),
                      equalTo(
                          CodeIncubatingAttributes.CODE_NAMESPACE,
                          AbstractOpenTelemetryAppenderTest.class.getName()),
                      equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "logWithExtras"),
                      satisfies(
                          CodeIncubatingAttributes.CODE_LINENO, lineNo -> lineNo.isGreaterThan(1)),
                      equalTo(
                          AttributeKey.stringArrayKey("logback.marker"),
                          Collections.singletonList(markerName)));

              LogRecordData logRecordData = AssertAccess.getActual(logRecord);
              assertThat(logRecordData.getTimestampEpochNanos())
                  .isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(start.toEpochMilli()))
                  .isLessThanOrEqualTo(
                      TimeUnit.SECONDS.toNanos(now.getEpochSecond()) + now.getNano());
            });
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

    getTesting()
        .waitAndAssertLogRecords(
            logRecord ->
                logRecord
                    .hasResource(resource)
                    .hasInstrumentationScope(instrumentationScopeInfo)
                    .hasBody("log message 1")
                    .hasTotalAttributeCount(2 + 4) // 4 code attributes
                    .hasAttributesSatisfying(
                        equalTo(AttributeKey.stringKey("key1"), "val1"),
                        equalTo(AttributeKey.stringKey("key2"), "val2")));
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

    getTesting()
        .waitAndAssertLogRecords(
            logRecord ->
                logRecord
                    .hasResource(resource)
                    .hasInstrumentationScope(instrumentationScopeInfo)
                    .hasBody("log message 1")
                    .hasTotalAttributeCount(1 + 4) // 4 code attributes
                    .hasAttributesSatisfying(
                        equalTo(AttributeKey.stringKey("test-property"), "test-value")));
  }
}
