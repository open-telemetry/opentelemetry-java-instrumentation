/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v1_2;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.semconv.ExceptionAttributes;
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.stream.Stream;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.apache.log4j.helpers.Loader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class Log4j1Test {

  static {
    // this is needed because log4j1 incorrectly thinks the initial releases of Java 10-19
    // (which have no '.' in their versions since there is no minor version) are Java 1.1,
    // which is before ThreadLocal was introduced and so log4j1 disables MDC functionality
    // (and the MDC tests below fail)
    try {
      Field java1 = Loader.class.getDeclaredField("java1");
      java1.setAccessible(true);
      java1.set(null, false);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = Logger.getLogger("abc");

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
      LoggerMethod loggerMethod,
      ExceptionLoggerMethod exceptionLoggerMethod,
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
          "parent", () -> performLogging(loggerMethod, exceptionLoggerMethod, logException));
    } else {
      performLogging(loggerMethod, exceptionLoggerMethod, logException);
    }

    // then
    if (withParent) {
      testing.waitForTraces(1);
    }

    if (expectedSeverity != null) {
      LogRecordData log = testing.waitForLogRecords(1).get(0);
      assertThat(log)
          .hasBody("xyz")
          .hasInstrumentationScope(InstrumentationScopeInfo.builder(expectedLoggerName).build())
          .hasSeverity(expectedSeverity)
          .hasSeverityText(expectedSeverityText);

      assertThat(log.getTimestampEpochNanos())
          .isGreaterThanOrEqualTo(MILLISECONDS.toNanos(start.toEpochMilli()))
          .isLessThanOrEqualTo(MILLISECONDS.toNanos(Instant.now().toEpochMilli()));

      if (logException) {
        assertThat(log)
            .hasAttributesSatisfyingExactly(
                equalTo(ThreadIncubatingAttributes.THREAD_NAME, Thread.currentThread().getName()),
                equalTo(ThreadIncubatingAttributes.THREAD_ID, Thread.currentThread().getId()),
                equalTo(ExceptionAttributes.EXCEPTION_TYPE, IllegalStateException.class.getName()),
                equalTo(ExceptionAttributes.EXCEPTION_MESSAGE, "hello"),
                satisfies(
                    ExceptionAttributes.EXCEPTION_STACKTRACE,
                    v -> v.contains(Log4j1Test.class.getName())));
      } else {
        assertThat(log)
            .hasAttributesSatisfyingExactly(
                equalTo(ThreadIncubatingAttributes.THREAD_NAME, Thread.currentThread().getName()),
                equalTo(ThreadIncubatingAttributes.THREAD_ID, Thread.currentThread().getId()));
      }

      if (withParent) {
        assertThat(log).hasSpanContext(testing.spans().get(0).getSpanContext());
      } else {
        assertThat(log.getSpanContext().isValid()).isFalse();
      }

    } else {
      Thread.sleep(500); // sleep a bit just to make sure no log is captured
      assertThat(testing.logRecords()).isEmpty();
    }
  }

  @Test
  void testMdc() {
    MDC.put("key1", "val1");
    MDC.put("key2", "val2");
    try {
      logger.info("xyz");
    } finally {
      MDC.remove("key1");
      MDC.remove("key2");
    }

    LogRecordData log = testing.waitForLogRecords(1).get(0);
    assertThat(log)
        .hasBody("xyz")
        .hasInstrumentationScope(InstrumentationScopeInfo.builder("abc").build())
        .hasSeverity(Severity.INFO)
        .hasSeverityText("INFO")
        .hasAttributesSatisfyingExactly(
            equalTo(AttributeKey.stringKey("key1"), "val1"),
            equalTo(AttributeKey.stringKey("key2"), "val2"),
            equalTo(ThreadIncubatingAttributes.THREAD_NAME, Thread.currentThread().getName()),
            equalTo(ThreadIncubatingAttributes.THREAD_ID, Thread.currentThread().getId()));
  }

  private static void performLogging(
      LoggerMethod loggerMethod,
      ExceptionLoggerMethod exceptionLoggerMethod,
      boolean logException) {
    if (logException) {
      exceptionLoggerMethod.call(logger, "xyz", new IllegalStateException("hello"));
    } else {
      loggerMethod.call(logger, "xyz");
    }
  }

  @FunctionalInterface
  interface LoggerMethod {
    void call(Logger logger, String msg);
  }

  @FunctionalInterface
  interface ExceptionLoggerMethod {
    void call(Logger logger, String msg, Exception e);
  }
}
