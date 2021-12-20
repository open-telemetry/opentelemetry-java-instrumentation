/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.sdk.logs.data.Severity
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

import java.util.logging.Level
import java.util.logging.Logger

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace
import static org.assertj.core.api.Assertions.assertThat
import static org.awaitility.Awaitility.await

class JavaUtilLoggingTest extends AgentInstrumentationSpecification {

  private static final Logger logger = Logger.getLogger("abc")

  def "test method=#testMethod with exception=#exception and parent=#parent"() {
    when:
    if (parent) {
      runUnderTrace("parent") {
        if (exception) {
          logger.log(Level."${testMethod.toUpperCase()}", "xyz", new IllegalStateException("hello"))
        } else {
          logger."$testMethod"("xyz")
        }
      }
    } else {
      if (exception) {
        logger.log(Level."${testMethod.toUpperCase()}", "xyz", new IllegalStateException("hello"))
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
      assertThat(log.getInstrumentationLibraryInfo().getName()).isEqualTo("abc")
      assertThat(log.getSeverity()).isEqualTo(severity)
      assertThat(log.getSeverityText()).isEqualTo(severityText)
      if (exception) {
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_TYPE)).isEqualTo(IllegalStateException.class.getName())
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_MESSAGE)).isEqualTo("hello")
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_STACKTRACE)).contains(JavaUtilLoggingTest.name)
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
        ["fine", null, null],
        ["info", Severity.INFO, "INFO"],
        ["warning", Severity.WARN, "WARNING"],
        ["severe", Severity.ERROR, "SEVERE"]
      ],
      [true, false],
      [true, false]
    ].combinations()

    testMethod = args[0]
    severity = args[1]
    severityText = args[2]
  }
}
