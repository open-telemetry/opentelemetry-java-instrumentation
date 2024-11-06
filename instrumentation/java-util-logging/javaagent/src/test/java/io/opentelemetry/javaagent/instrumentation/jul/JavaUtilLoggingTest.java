/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jul;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JavaUtilLoggingTest {

  private static final Logger logger = Logger.getLogger("abc");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static Stream<Arguments> provideParameters() {
    return Stream.of(
        Arguments.of(false, false, false),
        Arguments.of(false, false, true),
        Arguments.of(false, true, false),
        Arguments.of(false, true, true),
        Arguments.of(true, false, false),
        Arguments.of(true, false, true),
        Arguments.of(true, true, false),
        Arguments.of(true, true, true));
  }

  @ParameterizedTest
  @MethodSource("provideParameters")
  public void test(boolean withParam, boolean logException, boolean withParent)
      throws InterruptedException {
    test(Level.FINE, Logger::fine, withParam, logException, withParent, null, null, null);
    testing.clearData();
    test(
        Level.INFO,
        Logger::info,
        withParam,
        logException,
        withParent,
        "abc",
        Severity.INFO,
        "INFO");
    testing.clearData();
    test(
        Level.WARNING,
        Logger::warning,
        withParam,
        logException,
        withParent,
        "abc",
        Severity.WARN,
        "WARNING");
    testing.clearData();
    test(
        Level.SEVERE,
        Logger::severe,
        withParam,
        logException,
        withParent,
        "abc",
        Severity.ERROR,
        "SEVERE");
    testing.clearData();
  }

  private static void test(
      Level level,
      LoggerMethod loggerMethod,
      boolean withParam,
      boolean logException,
      boolean withParent,
      String expectedLoggerName,
      Severity expectedSeverity,
      String expectedSeverityText)
      throws InterruptedException {

    // when
    if (withParent) {
      testing.runWithSpan(
          "parent", () -> performLogging(level, loggerMethod, withParam, logException));
    } else {
      performLogging(level, loggerMethod, withParam, logException);
    }

    // then
    if (withParent) {
      testing.waitForTraces(1);
    }

    if (expectedSeverity != null) {
      LogRecordData log = testing.waitForLogRecords(1).get(0);
      assertThat(log)
          .hasBody(withParam ? "xyz: 123" : "xyz")
          .hasInstrumentationScope(InstrumentationScopeInfo.builder(expectedLoggerName).build())
          .hasSeverity(expectedSeverity)
          .hasSeverityText(expectedSeverityText);
      if (logException) {
        assertThat(log)
            .hasAttributesSatisfyingExactly(
                equalTo(ThreadIncubatingAttributes.THREAD_NAME, Thread.currentThread().getName()),
                equalTo(ThreadIncubatingAttributes.THREAD_ID, Thread.currentThread().getId()),
                equalTo(EXCEPTION_TYPE, IllegalStateException.class.getName()),
                equalTo(EXCEPTION_MESSAGE, "hello"),
                satisfies(
                    EXCEPTION_STACKTRACE, v -> v.contains(JavaUtilLoggingTest.class.getName())));
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

  private static void performLogging(
      Level level, LoggerMethod loggerMethod, boolean withParam, boolean logException) {
    if (logException) {
      if (withParam) {
        // this is the best j.u.l. can do
        logger.log(level, new IllegalStateException("hello"), () -> "xyz: 123");
      } else {
        logger.log(level, "xyz", new IllegalStateException("hello"));
      }
    } else {
      if (withParam) {
        logger.log(level, "xyz: {0}", 123);
      } else {
        loggerMethod.call(logger, "xyz");
      }
    }
  }

  @FunctionalInterface
  interface LoggerMethod {
    void call(Logger logger, String msg);
  }
}
