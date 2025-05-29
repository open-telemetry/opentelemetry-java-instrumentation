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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

class LogbackTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger abcLogger = LoggerFactory.getLogger("abc");
  private static final Logger defLogger = LoggerFactory.getLogger("def");

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
    test(abcLogger, Logger::debug, Logger::debug, logException, withParent, null, null, null);
    testing.clearData();
    test(
        abcLogger,
        Logger::info,
        Logger::info,
        logException,
        withParent,
        "abc",
        Severity.INFO,
        "INFO");
    testing.clearData();
    test(
        abcLogger,
        Logger::warn,
        Logger::warn,
        logException,
        withParent,
        "abc",
        Severity.WARN,
        "WARN");
    testing.clearData();
    test(
        abcLogger,
        Logger::error,
        Logger::error,
        logException,
        withParent,
        "abc",
        Severity.ERROR,
        "ERROR");
    testing.clearData();
    test(defLogger, Logger::debug, Logger::debug, logException, withParent, null, null, null);
    testing.clearData();
    test(defLogger, Logger::info, Logger::info, logException, withParent, null, null, null);
    testing.clearData();
    test(
        defLogger,
        Logger::warn,
        Logger::warn,
        logException,
        withParent,
        "def",
        Severity.WARN,
        "WARN");
    testing.clearData();
    test(
        defLogger,
        Logger::error,
        Logger::error,
        logException,
        withParent,
        "def",
        Severity.ERROR,
        "ERROR");
    testing.clearData();
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static void test(
      Logger logger,
      OneArgLoggerMethod oneArgLoggerMethod,
      TwoArgLoggerMethod twoArgLoggerMethod,
      boolean logException,
      boolean withParent,
      String expectedLoggerName,
      Severity expectedSeverity,
      String expectedSeverityText)
      throws InterruptedException {

    // when
    if (withParent) {
      testing.runWithSpan(
          "parent",
          () -> performLogging(logger, oneArgLoggerMethod, twoArgLoggerMethod, logException));
    } else {
      performLogging(logger, oneArgLoggerMethod, twoArgLoggerMethod, logException);
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
                        equalTo(
                            ThreadIncubatingAttributes.THREAD_NAME,
                            Thread.currentThread().getName()),
                        equalTo(
                            ThreadIncubatingAttributes.THREAD_ID, Thread.currentThread().getId()),
                        equalTo(
                            CodeIncubatingAttributes.CODE_NAMESPACE, LogbackTest.class.getName()),
                        equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "performLogging"),
                        satisfies(
                            CodeIncubatingAttributes.CODE_LINENO, AbstractLongAssert::isPositive),
                        equalTo(CodeIncubatingAttributes.CODE_FILEPATH, "LogbackTest.java")));
            if (logException) {
              attributeAsserts.addAll(
                  Arrays.asList(
                      equalTo(EXCEPTION_TYPE, IllegalStateException.class.getName()),
                      equalTo(EXCEPTION_MESSAGE, "hello"),
                      satisfies(
                          EXCEPTION_STACKTRACE, v -> v.contains(LogbackTest.class.getName()))));
            }
            logRecord.hasAttributesSatisfyingExactly(attributeAsserts);
          });
    } else {
      Thread.sleep(500); // sleep a bit just to make sure no log is captured
      assertThat(testing.logRecords()).isEmpty();
    }
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void testMdc() {
    MDC.put("key1", "val1");
    MDC.put("key2", "val2");
    try {
      abcLogger.info("xyz: {}", 123);
    } finally {
      MDC.clear();
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
                    equalTo(
                        ThreadIncubatingAttributes.THREAD_NAME, Thread.currentThread().getName()),
                    equalTo(ThreadIncubatingAttributes.THREAD_ID, Thread.currentThread().getId()),
                    equalTo(CodeIncubatingAttributes.CODE_NAMESPACE, LogbackTest.class.getName()),
                    equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "testMdc"),
                    satisfies(CodeIncubatingAttributes.CODE_LINENO, AbstractLongAssert::isPositive),
                    equalTo(CodeIncubatingAttributes.CODE_FILEPATH, "LogbackTest.java")));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  public void testMarker() {

    String markerName = "aMarker";
    Marker marker = MarkerFactory.getMarker(markerName);

    abcLogger.info(marker, "Message");

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(ThreadIncubatingAttributes.THREAD_NAME, Thread.currentThread().getName()),
                equalTo(ThreadIncubatingAttributes.THREAD_ID, Thread.currentThread().getId()),
                equalTo(
                    AttributeKey.stringArrayKey("logback.marker"),
                    Collections.singletonList(markerName)),
                equalTo(CodeIncubatingAttributes.CODE_NAMESPACE, LogbackTest.class.getName()),
                equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "testMarker"),
                satisfies(CodeIncubatingAttributes.CODE_LINENO, AbstractLongAssert::isPositive),
                equalTo(CodeIncubatingAttributes.CODE_FILEPATH, "LogbackTest.java")));
  }

  private static void performLogging(
      Logger logger,
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
