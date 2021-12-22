/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.sdk.logs.data.Severity
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import spock.lang.Unroll

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace
import static org.assertj.core.api.Assertions.assertThat
import static org.awaitility.Awaitility.await

class LogbackTest extends AgentInstrumentationSpecification {

  private static final Logger abcLogger = LoggerFactory.getLogger("abc");
  private static final Logger defLogger = LoggerFactory.getLogger("def");

  @Unroll
  def "test logger=#loggerName method=#testMethod with exception=#exception and parent=#parent"() {
    when:
    if (parent) {
      runUnderTrace("parent") {
        if (exception) {
          logger."$testMethod"("xyz", new IllegalStateException("hello"))
        } else {
          logger."$testMethod"("xyz")
        }
      }
    } else {
      if (exception) {
        logger."$testMethod"("xyz", new IllegalStateException("hello"))
      } else {
        logger."$testMethod"("xyz")
      }
    }

    then:
    if (parent) {
      waitForTraces(1)
    }

    if (severity != null) {
      await()
        .untilAsserted(
          () -> {
            assertThat(logs).hasSize(1)
          })
      def log = logs.get(0)
      assertThat(log.getBody().asString()).isEqualTo("xyz")
      assertThat(log.getInstrumentationLibraryInfo().getName()).isEqualTo(loggerName)
      assertThat(log.getSeverity()).isEqualTo(severity)
      assertThat(log.getSeverityText()).isEqualTo(severityText)
      if (exception) {
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_TYPE)).isEqualTo(IllegalStateException.class.getName())
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_MESSAGE)).isEqualTo("hello")
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_STACKTRACE)).contains(LogbackTest.name)
      } else {
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_TYPE)).isNull()
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_MESSAGE)).isNull()
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_STACKTRACE)).isNull()
      }
      if (parent) {
        assertThat(log.getSpanContext()).isEqualTo(traces.get(0).get(0).getSpanContext())
      } else {
        assertThat(log.getSpanContext().isValid()).isFalse()
      }
    } else {
      Thread.sleep(500) // sleep a bit just to make sure no span is captured
      logs.size() == 0
    }

    where:
    [args, exception, parent] << [
      [
        [abcLogger, "abc", "debug", null, null],
        [abcLogger, "abc", "info", Severity.INFO, "INFO"],
        [abcLogger, "abc", "warn", Severity.WARN, "WARN"],
        [abcLogger, "abc", "error", Severity.ERROR, "ERROR"],
        [defLogger, "def", "debug", null, null],
        [defLogger, "def", "info", null, null],
        [defLogger, "def", "warn", Severity.WARN, "WARN"],
        [defLogger, "def", "error", Severity.ERROR, "ERROR"]
      ],
      [true, false],
      [true, false]
    ].combinations()

    logger = args[0]
    loggerName = args[1]
    testMethod = args[2]
    severity = args[3]
    severityText = args[4]
  }

  def "test mdc"() {
    when:
    MDC.put("key1", "val1")
    MDC.put("key2", "val2")
    try {
      abcLogger.info("xyz")
    } finally {
      MDC.clear()
    }

    then:

    await()
      .untilAsserted(
        () -> {
          assertThat(logs).hasSize(1)
        })
    def log = logs.get(0)
    assertThat(log.getBody().asString()).isEqualTo("xyz")
    assertThat(log.getInstrumentationLibraryInfo().getName()).isEqualTo("abc")
    assertThat(log.getSeverity()).isEqualTo(Severity.INFO)
    assertThat(log.getSeverityText()).isEqualTo("INFO")
    assertThat(log.getAttributes().size()).isEqualTo(2)
    assertThat(log.getAttributes().get(AttributeKey.stringKey("logback.mdc.key1"))).isEqualTo("val1")
    assertThat(log.getAttributes().get(AttributeKey.stringKey("logback.mdc.key2"))).isEqualTo("val2")
  }
}
