/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_FILEPATH;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_FUNCTION;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_LINENO;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_NAMESPACE;
import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_ID;
import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_NAME;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.message.StringMapMessage;
import org.apache.logging.log4j.message.StructuredDataMessage;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AssertAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("deprecation") // using deprecated semconv
class Log4j2Test {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = LogManager.getLogger("abc");

  private static Stream<Arguments> provideParameters() {
    return Stream.of(
        Arguments.of(false, false),
        Arguments.of(false, true),
        Arguments.of(true, false),
        Arguments.of(true, true));
  }

  @ParameterizedTest
  @MethodSource("provideParameters")
  public void test(boolean logException, boolean withParent) throws InterruptedException {
    test(Logger::debug, Logger::debug, logException, withParent, null, null, null);
    testing.clearData();
    test(Logger::info, Logger::info, logException, withParent, "abc", Severity.INFO, "INFO");
    testing.clearData();
    test(Logger::warn, Logger::warn, logException, withParent, "abc", Severity.WARN, "WARN");
    testing.clearData();
    test(Logger::error, Logger::error, logException, withParent, "abc", Severity.ERROR, "ERROR");
    testing.clearData();
  }

  private static void test(
      OneArgLoggerMethod oneArgLoggerMethod,
      TwoArgLoggerMethod twoArgLoggerMethod,
      boolean logException,
      boolean withParent,
      String expectedLoggerName,
      Severity expectedSeverity,
      String expectedSeverityText)
      throws InterruptedException {

    Instant start = Instant.now();

    // when
    if (withParent) {
      testing.runWithSpan(
          "parent", () -> performLogging(oneArgLoggerMethod, twoArgLoggerMethod, logException));
    } else {
      performLogging(oneArgLoggerMethod, twoArgLoggerMethod, logException);
    }

    // then
    if (withParent) {
      testing.waitForTraces(1);
    }

    if (expectedSeverity != null) {
      testing.waitAndAssertLogRecords(
          logRecord -> {
            logRecord
                .hasBody("xyz: 123")
                .hasInstrumentationScope(
                    InstrumentationScopeInfo.builder(expectedLoggerName).build())
                .hasSeverity(expectedSeverity)
                .hasSeverityText(expectedSeverityText)
                .hasSpanContext(
                    withParent
                        ? testing.spans().get(0).getSpanContext()
                        : SpanContext.getInvalid());

            List<AttributeAssertion> attributeAsserts =
                new ArrayList<>(
                    Arrays.asList(
                        equalTo(THREAD_NAME, Thread.currentThread().getName()),
                        equalTo(THREAD_ID, Thread.currentThread().getId()),
                        equalTo(CODE_NAMESPACE, Log4j2Test.class.getName()),
                        equalTo(CODE_FUNCTION, "performLogging"),
                        satisfies(CODE_LINENO, AbstractLongAssert::isPositive),
                        equalTo(CODE_FILEPATH, "Log4j2Test.java")));
            if (logException) {
              attributeAsserts.addAll(
                  Arrays.asList(
                      equalTo(EXCEPTION_TYPE, IllegalStateException.class.getName()),
                      equalTo(EXCEPTION_MESSAGE, "hello"),
                      satisfies(
                          EXCEPTION_STACKTRACE, v -> v.contains(Log4j2Test.class.getName()))));
            }
            logRecord.hasAttributesSatisfyingExactly(attributeAsserts);

            LogRecordData logRecordData = AssertAccess.getActual(logRecord);
            assertThat(logRecordData.getTimestampEpochNanos())
                .isGreaterThanOrEqualTo(MILLISECONDS.toNanos(start.toEpochMilli()))
                .isLessThanOrEqualTo(MILLISECONDS.toNanos(Instant.now().toEpochMilli()));
          });
    } else {
      Thread.sleep(500); // sleep a bit just to make sure no log is captured
      assertThat(testing.logRecords()).isEmpty();
    }
  }

  @Test
  void testContextData() {
    ThreadContext.put("key1", "val1");
    ThreadContext.put("key2", "val2");
    try {
      logger.info("xyz: {}", 123);
    } finally {
      ThreadContext.clearMap();
    }

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasBody("xyz: 123")
                .hasInstrumentationScope(InstrumentationScopeInfo.builder("abc").build())
                .hasSeverity(Severity.INFO)
                .hasSeverityText("INFO")
                .hasAttributesSatisfyingExactly(
                    equalTo(AttributeKey.stringKey("key1"), "val1"),
                    equalTo(AttributeKey.stringKey("key2"), "val2"),
                    equalTo(THREAD_NAME, Thread.currentThread().getName()),
                    equalTo(THREAD_ID, Thread.currentThread().getId()),
                    equalTo(CODE_NAMESPACE, Log4j2Test.class.getName()),
                    equalTo(CODE_FUNCTION, "testContextData"),
                    satisfies(CODE_LINENO, AbstractLongAssert::isPositive),
                    equalTo(CODE_FILEPATH, "Log4j2Test.java")));
  }

  @Test
  void testStringMapMessage() {
    StringMapMessage message = new StringMapMessage();
    message.put("key1", "val1");
    message.put("key2", "val2");
    logger.info(message);

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasBody((Value<?>) null)
                .hasInstrumentationScope(InstrumentationScopeInfo.builder("abc").build())
                .hasSeverity(Severity.INFO)
                .hasSeverityText("INFO")
                .hasAttributesSatisfyingExactly(
                    equalTo(AttributeKey.stringKey("log4j.map_message.key1"), "val1"),
                    equalTo(AttributeKey.stringKey("log4j.map_message.key2"), "val2"),
                    equalTo(THREAD_NAME, Thread.currentThread().getName()),
                    equalTo(THREAD_ID, Thread.currentThread().getId()),
                    equalTo(CODE_NAMESPACE, Log4j2Test.class.getName()),
                    equalTo(CODE_FUNCTION, "testStringMapMessage"),
                    satisfies(CODE_LINENO, AbstractLongAssert::isPositive),
                    equalTo(CODE_FILEPATH, "Log4j2Test.java")));
  }

  @Test
  void testStringMapMessageWithSpecialAttribute() {
    StringMapMessage message = new StringMapMessage();
    message.put("key1", "val1");
    message.put("message", "val2");
    logger.info(message);

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasBody("val2")
                .hasInstrumentationScope(InstrumentationScopeInfo.builder("abc").build())
                .hasSeverity(Severity.INFO)
                .hasSeverityText("INFO")
                .hasAttributesSatisfyingExactly(
                    equalTo(AttributeKey.stringKey("log4j.map_message.key1"), "val1"),
                    equalTo(THREAD_NAME, Thread.currentThread().getName()),
                    equalTo(THREAD_ID, Thread.currentThread().getId()),
                    equalTo(CODE_NAMESPACE, Log4j2Test.class.getName()),
                    equalTo(CODE_FUNCTION, "testStringMapMessageWithSpecialAttribute"),
                    satisfies(CODE_LINENO, AbstractLongAssert::isPositive),
                    equalTo(CODE_FILEPATH, "Log4j2Test.java")));
  }

  @Test
  void testStructuredDataMapMessage() {
    StructuredDataMessage message = new StructuredDataMessage("an id", "a message", "a type");
    message.put("key1", "val1");
    message.put("key2", "val2");
    logger.info(message);

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasBody("a message")
                .hasInstrumentationScope(InstrumentationScopeInfo.builder("abc").build())
                .hasSeverity(Severity.INFO)
                .hasSeverityText("INFO")
                .hasAttributesSatisfyingExactly(
                    equalTo(AttributeKey.stringKey("log4j.map_message.key1"), "val1"),
                    equalTo(AttributeKey.stringKey("log4j.map_message.key2"), "val2"),
                    equalTo(THREAD_NAME, Thread.currentThread().getName()),
                    equalTo(THREAD_ID, Thread.currentThread().getId()),
                    equalTo(CODE_NAMESPACE, Log4j2Test.class.getName()),
                    equalTo(CODE_FUNCTION, "testStructuredDataMapMessage"),
                    satisfies(CODE_LINENO, AbstractLongAssert::isPositive),
                    equalTo(CODE_FILEPATH, "Log4j2Test.java")));
  }

  @Test
  public void testMarker() {
    String markerName = "aMarker";
    Marker marker = MarkerManager.getMarker(markerName);

    logger.info(marker, "Message");

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(THREAD_NAME, Thread.currentThread().getName()),
                equalTo(THREAD_ID, Thread.currentThread().getId()),
                equalTo(CODE_NAMESPACE, Log4j2Test.class.getName()),
                equalTo(CODE_FUNCTION, "testMarker"),
                satisfies(CODE_LINENO, AbstractLongAssert::isPositive),
                equalTo(CODE_FILEPATH, "Log4j2Test.java"),
                equalTo(AttributeKey.stringKey("log4j.marker"), markerName)));
  }

  private static void performLogging(
      OneArgLoggerMethod oneArgLoggerMethod,
      TwoArgLoggerMethod twoArgLoggerMethod,
      boolean logException) {
    if (logException) {
      twoArgLoggerMethod.call(logger, "xyz: {}", 123, new IllegalStateException("hello"));
    } else {
      oneArgLoggerMethod.call(logger, "xyz: {}", 123);
    }
  }

  @FunctionalInterface
  interface OneArgLoggerMethod {
    void call(Logger logger, String msg, Object arg);
  }

  @FunctionalInterface
  interface TwoArgLoggerMethod {
    void call(Logger logger, String msg, Object arg1, Object arg2);
  }
}
