/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.sdk.testing.assertj.LogAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.LogData;
import io.opentelemetry.sdk.logs.data.Severity;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class LogbackTest extends AgentInstrumentationSpecification {

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
          () -> {
            performLogging(logger, oneArgLoggerMethod, twoArgLoggerMethod, logException);
          });
    } else {
      performLogging(logger, oneArgLoggerMethod, twoArgLoggerMethod, logException);
    }

    // then
    if (withParent) {
      testing.waitForTraces(1);
    }

    if (expectedSeverity != null) {
      await().untilAsserted(() -> assertThat(testing.logs().size()).isEqualTo(1));

      LogData log = testing.logs().get(0);
      assertThat(log)
          .hasBody("xyz: 123")
          // TODO (trask) why is version "" instead of null?
          .hasInstrumentationScope(
              InstrumentationScopeInfo.builder(expectedLoggerName).setVersion("").build())
          .hasSeverity(expectedSeverity)
          .hasSeverityText(expectedSeverityText);
      if (logException) {
        assertThat(log)
            .hasAttributesSatisfying(
                attributes ->
                    assertThat(attributes)
                        .hasSize(9)
                        .containsEntry(
                            SemanticAttributes.EXCEPTION_TYPE,
                            IllegalStateException.class.getName())
                        .containsEntry(SemanticAttributes.EXCEPTION_MESSAGE, "hello")
                        .hasEntrySatisfying(
                            SemanticAttributes.EXCEPTION_STACKTRACE,
                            value -> assertThat(value).contains(LogbackTest.class.getName())));
      } else {
        assertThat(log.getAttributes()).hasSize(6);
      }

      assertThat(log)
          .hasAttributesSatisfying(
              attributes ->
                  assertThat(attributes)
                      .containsEntry(
                          SemanticAttributes.THREAD_NAME, Thread.currentThread().getName())
                      .containsEntry(SemanticAttributes.THREAD_ID, Thread.currentThread().getId())
                      .containsEntry(SemanticAttributes.CODE_NAMESPACE, LogbackTest.class.getName())
                      .containsEntry(SemanticAttributes.CODE_FUNCTION, "performLogging")
                      .hasEntrySatisfying(
                          SemanticAttributes.CODE_LINENO, value -> assertThat(value).isPositive())
                      .containsEntry(SemanticAttributes.CODE_FILEPATH, "LogbackTest.java"));

      if (withParent) {
        assertThat(log.getSpanContext()).isEqualTo(testing.spans().get(0).getSpanContext());
      } else {
        assertThat(log.getSpanContext().isValid()).isFalse();
      }

    } else {
      Thread.sleep(500); // sleep a bit just to make sure no log is captured
      assertThat(testing.logs()).isEmpty();
    }
  }

  @Test
  void testMdc() {
    MDC.put("key1", "val1");
    MDC.put("key2", "val2");
    try {
      abcLogger.info("xyz: {}", 123);
    } finally {
      MDC.clear();
    }

    await().untilAsserted(() -> assertThat(testing.logs().size()).isEqualTo(1));

    LogData log = getLogs().get(0);
    assertThat(log)
        .hasBody("xyz: 123")
        // TODO (trask) why is version "" instead of null?
        .hasInstrumentationScope(InstrumentationScopeInfo.builder("abc").setVersion("").build())
        .hasSeverity(Severity.INFO)
        .hasSeverityText("INFO")
        // TODO (trask) convert to hasAttributesSatisfyingExactly once that's available for logs
        .hasAttributesSatisfying(
            attributes ->
                assertThat(attributes)
                    .hasSize(8)
                    .containsEntry(AttributeKey.stringKey("logback.mdc.key1"), "val1")
                    .containsEntry(AttributeKey.stringKey("logback.mdc.key2"), "val2")
                    .containsEntry(SemanticAttributes.THREAD_NAME, Thread.currentThread().getName())
                    .containsEntry(SemanticAttributes.THREAD_ID, Thread.currentThread().getId())
                    .containsEntry(SemanticAttributes.CODE_NAMESPACE, LogbackTest.class.getName())
                    .containsEntry(SemanticAttributes.CODE_FUNCTION, "testMdc")
                    .hasEntrySatisfying(
                        SemanticAttributes.CODE_LINENO, value -> assertThat(value).isPositive())
                    .containsEntry(SemanticAttributes.CODE_FILEPATH, "LogbackTest.java"));
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
