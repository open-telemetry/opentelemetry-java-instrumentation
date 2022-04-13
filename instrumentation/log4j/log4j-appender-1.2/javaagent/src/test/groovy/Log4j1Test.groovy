/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.sdk.logs.data.Severity
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.log4j.Logger
import org.apache.log4j.MDC
import spock.lang.Unroll

import static org.assertj.core.api.Assertions.assertThat
import static org.awaitility.Awaitility.await

class Log4j1Test extends AgentInstrumentationSpecification {

  private static final Logger logger = Logger.getLogger("abc")

  @Unroll
  def "test method=#testMethod with exception=#exception and parent=#parent"() {
    when:
    if (parent) {
      runWithSpan("parent") {
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
      assertThat(log.getInstrumentationScopeInfo().getName()).isEqualTo("abc")
      assertThat(log.getSeverity()).isEqualTo(severity)
      assertThat(log.getSeverityText()).isEqualTo(severityText)
      if (exception) {
        assertThat(log.getAttributes().size()).isEqualTo(5)
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_TYPE)).isEqualTo(IllegalStateException.getName())
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_MESSAGE)).isEqualTo("hello")
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_STACKTRACE)).contains(Log4j1Test.name)
      } else {
        assertThat(log.getAttributes().size()).isEqualTo(2)
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_TYPE)).isNull()
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_MESSAGE)).isNull()
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_STACKTRACE)).isNull()
      }
      assertThat(log.getAttributes().get(SemanticAttributes.THREAD_NAME)).isEqualTo(Thread.currentThread().getName())
      assertThat(log.getAttributes().get(SemanticAttributes.THREAD_ID)).isEqualTo(Thread.currentThread().getId())
      if (parent) {
        assertThat(log.getSpanContext()).isEqualTo(traces.get(0).get(0).getSpanContext())
      } else {
        assertThat(log.getSpanContext().isValid()).isFalse()
      }
    } else {
      Thread.sleep(500) // sleep a bit just to make sure no log is captured
      logs.size() == 0
    }

    where:
    [args, exception, parent] << [
      [
        ["debug", null, null],
        ["info", Severity.INFO, "INFO"],
        ["warn", Severity.WARN, "WARN"],
        ["error", Severity.ERROR, "ERROR"]
      ],
      [true, false],
      [true, false]
    ].combinations()

    testMethod = args[0]
    severity = args[1]
    severityText = args[2]
  }

  def "test mdc"() {
    when:
    MDC.put("key1", "val1")
    MDC.put("key2", "val2")
    try {
      logger.info("xyz")
    } finally {
      MDC.remove("key1")
      MDC.remove("key2")
    }

    then:

    await()
      .untilAsserted(
        () -> {
          assertThat(logs).hasSize(1)
        })
    def log = logs.get(0)
    assertThat(log.getBody().asString()).isEqualTo("xyz")
    assertThat(log.getInstrumentationScopeInfo().getName()).isEqualTo("abc")
    assertThat(log.getSeverity()).isEqualTo(Severity.INFO)
    assertThat(log.getSeverityText()).isEqualTo("INFO")
    assertThat(log.getAttributes().size()).isEqualTo(4)
    assertThat(log.getAttributes().get(AttributeKey.stringKey("log4j.mdc.key1"))).isEqualTo("val1")
    assertThat(log.getAttributes().get(AttributeKey.stringKey("log4j.mdc.key2"))).isEqualTo("val2")
    assertThat(log.getAttributes().get(SemanticAttributes.THREAD_NAME)).isEqualTo(Thread.currentThread().getName())
    assertThat(log.getAttributes().get(SemanticAttributes.THREAD_ID)).isEqualTo(Thread.currentThread().getId())
  }
}
