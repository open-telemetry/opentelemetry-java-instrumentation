/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jbosslogmanager.appender.v1_1;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.AssertAccess;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.MDC;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JbossLogmanagerTest {

  private static final Logger logger = LogContext.getLogContext().getLogger("abc");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static {
    logger.setLevel(Level.INFO);
  }

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
    test(
        java.util.logging.Level.FINE,
        java.util.logging.Logger::fine,
        withParam,
        logException,
        withParent,
        null,
        null,
        null);
    testing.clearData();
    test(
        java.util.logging.Level.INFO,
        java.util.logging.Logger::info,
        withParam,
        logException,
        withParent,
        "abc",
        Severity.INFO,
        "INFO");
    testing.clearData();
    test(
        java.util.logging.Level.WARNING,
        java.util.logging.Logger::warning,
        withParam,
        logException,
        withParent,
        "abc",
        Severity.WARN,
        "WARNING");
    testing.clearData();
    test(
        java.util.logging.Level.SEVERE,
        java.util.logging.Logger::severe,
        withParam,
        logException,
        withParent,
        "abc",
        Severity.ERROR,
        "SEVERE");
    testing.clearData();
  }

  private static void test(
      java.util.logging.Level level,
      LoggerMethod loggerMethod,
      boolean withParam,
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
          "parent", () -> performLogging(level, loggerMethod, withParam, logException));
    } else {
      performLogging(level, loggerMethod, withParam, logException);
    }

    // then
    if (withParent) {
      testing.waitForTraces(1);
    }

    if (expectedSeverity != null) {
      testing.waitAndAssertLogRecords(
          logRecord -> {
            logRecord
                .hasBody(withParam ? "xyz: 123" : "xyz")
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
                            ThreadIncubatingAttributes.THREAD_ID, Thread.currentThread().getId())));
            if (logException) {
              attributeAsserts.addAll(
                  Arrays.asList(
                      equalTo(EXCEPTION_TYPE, IllegalStateException.class.getName()),
                      equalTo(EXCEPTION_MESSAGE, "hello"),
                      satisfies(
                          EXCEPTION_STACKTRACE,
                          v -> v.contains(JbossLogmanagerTest.class.getName()))));
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

  private static void performLogging(
      java.util.logging.Level level,
      LoggerMethod loggerMethod,
      boolean withParam,
      boolean logException) {
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

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasBody("xyz")
                .hasInstrumentationScope(InstrumentationScopeInfo.builder("abc").build())
                .hasSeverity(Severity.INFO)
                .hasSeverityText("INFO")
                .hasAttributesSatisfyingExactly(
                    equalTo(AttributeKey.stringKey("key1"), "val1"),
                    equalTo(AttributeKey.stringKey("key2"), "val2"),
                    equalTo(
                        ThreadIncubatingAttributes.THREAD_NAME, Thread.currentThread().getName()),
                    equalTo(ThreadIncubatingAttributes.THREAD_ID, Thread.currentThread().getId())));
  }

  @FunctionalInterface
  interface LoggerMethod {
    void call(java.util.logging.Logger logger, String msg);
  }
}
