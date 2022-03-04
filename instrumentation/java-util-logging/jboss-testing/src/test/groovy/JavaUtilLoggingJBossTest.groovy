/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.sdk.logs.data.Severity
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.jboss.logmanager.LogContext
import spock.lang.Shared
import spock.lang.Unroll

import java.util.logging.Level

import static org.assertj.core.api.Assertions.assertThat
import static org.awaitility.Awaitility.await

class JavaUtilLoggingJBossTest extends AgentInstrumentationSpecification {

  @Shared
  private final Object logger = LogContext.create().getLogger("abc")

  @Unroll
  def "test method=#testMethod with testArgs=#testArgs and parent=#parent"() {
    when:
    if (parent) {
      runWithSpan("parent") {
        if (testArgs == "exception") {
          logger.log(Level."${testMethod.toUpperCase()}", "xyz", new IllegalStateException("hello"))
        } else if (testArgs == "params") {
          logger.log(Level."${testMethod.toUpperCase()}", "xyz: {0}", 123)
        } else {
          logger."$testMethod"("xyz")
        }
      }
    } else {
      if (testArgs == "exception") {
        logger.log(Level."${testMethod.toUpperCase()}", "xyz", new IllegalStateException("hello"))
      } else if (testArgs == "params") {
        logger.log(Level."${testMethod.toUpperCase()}", "xyz: {0}", 123)
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
      if (testArgs == "params") {
        assertThat(log.getBody().asString()).isEqualTo("xyz: 123")
      } else {
        assertThat(log.getBody().asString()).isEqualTo("xyz")
      }
      assertThat(log.getInstrumentationLibraryInfo().getName()).isEqualTo("abc")
      assertThat(log.getSeverity()).isEqualTo(severity)
      assertThat(log.getSeverityText()).isEqualTo(severityText)
      if (testArgs == "exception") {
        assertThat(log.getAttributes().size()).isEqualTo(5)
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_TYPE)).isEqualTo(IllegalStateException.getName())
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_MESSAGE)).isEqualTo("hello")
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_STACKTRACE)).contains(JavaUtilLoggingJBossTest.name)
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
    [args, testArgs, parent] << [
      [
        ["fine", null, null],
        ["info", Severity.INFO, "INFO"],
        ["warning", Severity.WARN, "WARNING"],
        ["severe", Severity.ERROR, "SEVERE"]
      ],
      ["none", "exception", "param"],
      [true, false]
    ].combinations()

    testMethod = args[0]
    severity = args[1]
    severityText = args[2]
  }
}
