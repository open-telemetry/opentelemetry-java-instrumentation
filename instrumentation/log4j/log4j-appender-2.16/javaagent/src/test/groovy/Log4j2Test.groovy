/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.sdk.logs.data.Severity
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.ThreadContext
import org.apache.logging.log4j.message.StringMapMessage
import org.apache.logging.log4j.message.StructuredDataMessage
import spock.lang.Unroll

import static org.assertj.core.api.Assertions.assertThat
import static org.awaitility.Awaitility.await

class Log4j2Test extends AgentInstrumentationSpecification {

  private static final Logger logger = LogManager.getLogger("abc")

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
      assertThat(log.getInstrumentationLibraryInfo().getName()).isEqualTo("abc")
      assertThat(log.getSeverity()).isEqualTo(severity)
      assertThat(log.getSeverityText()).isEqualTo(severityText)
      if (exception) {
        assertThat(log.getAttributes().size()).isEqualTo(5)
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_TYPE)).isEqualTo(IllegalStateException.getName())
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_MESSAGE)).isEqualTo("hello")
        assertThat(log.getAttributes().get(SemanticAttributes.EXCEPTION_STACKTRACE)).contains(Log4j2Test.name)
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

  def "test context data"() {
    when:
    ThreadContext.put("key1", "val1")
    ThreadContext.put("key2", "val2")
    try {
      logger.info("xyz")
    } finally {
      ThreadContext.clearMap()
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
    assertThat(log.getAttributes().size()).isEqualTo(4)
    assertThat(log.getAttributes().get(AttributeKey.stringKey("log4j.context_data.key1"))).isEqualTo("val1")
    assertThat(log.getAttributes().get(AttributeKey.stringKey("log4j.context_data.key2"))).isEqualTo("val2")
    assertThat(log.getAttributes().get(SemanticAttributes.THREAD_NAME)).isEqualTo(Thread.currentThread().getName())
    assertThat(log.getAttributes().get(SemanticAttributes.THREAD_ID)).isEqualTo(Thread.currentThread().getId())
  }

  def "test string map message"() {
    when:
    StringMapMessage message = new StringMapMessage()
    message.put("key1", "val1")
    message.put("key2", "val2")
    logger.info(message)

    then:

    await()
      .untilAsserted(
        () -> {
          assertThat(logs).hasSize(1)
        })
    def log = logs.get(0)
    assertThat(log.getBody().asString()).isEmpty()
    assertThat(log.getInstrumentationLibraryInfo().getName()).isEqualTo("abc")
    assertThat(log.getSeverity()).isEqualTo(Severity.INFO)
    assertThat(log.getSeverityText()).isEqualTo("INFO")
    assertThat(log.getAttributes().size()).isEqualTo(4)
    assertThat(log.getAttributes().get(AttributeKey.stringKey("key1"))).isEqualTo("val1")
    assertThat(log.getAttributes().get(AttributeKey.stringKey("key2"))).isEqualTo("val2")
    assertThat(log.getAttributes().get(SemanticAttributes.THREAD_NAME)).isEqualTo(Thread.currentThread().getName())
    assertThat(log.getAttributes().get(SemanticAttributes.THREAD_ID)).isEqualTo(Thread.currentThread().getId())
  }

  def "test string map message with special attribute"() {
    when:
    StringMapMessage message = new StringMapMessage()
    message.put("key1", "val1")
    message.put("message", "val2")
    logger.info(message)

    then:

    await()
      .untilAsserted(
        () -> {
          assertThat(logs).hasSize(1)
        })
    def log = logs.get(0)
    assertThat(log.getBody().asString()).isEqualTo("val2")
    assertThat(log.getInstrumentationLibraryInfo().getName()).isEqualTo("abc")
    assertThat(log.getSeverity()).isEqualTo(Severity.INFO)
    assertThat(log.getSeverityText()).isEqualTo("INFO")
    assertThat(log.getAttributes().size()).isEqualTo(3)
    assertThat(log.getAttributes().get(AttributeKey.stringKey("key1"))).isEqualTo("val1")
    assertThat(log.getAttributes().get(SemanticAttributes.THREAD_NAME)).isEqualTo(Thread.currentThread().getName())
    assertThat(log.getAttributes().get(SemanticAttributes.THREAD_ID)).isEqualTo(Thread.currentThread().getId())
  }

  def "test structured data map message"() {
    when:
    StructuredDataMessage message = new StructuredDataMessage("an id", "a message", "a type")
    message.put("key1", "val1")
    message.put("key2", "val2")
    logger.info(message)

    then:

    await()
      .untilAsserted(
        () -> {
          assertThat(logs).hasSize(1)
        })
    def log = logs.get(0)
    assertThat(log.getBody().asString()).isEqualTo("a message")
    assertThat(log.getInstrumentationLibraryInfo().getName()).isEqualTo("abc")
    assertThat(log.getSeverity()).isEqualTo(Severity.INFO)
    assertThat(log.getSeverityText()).isEqualTo("INFO")
    assertThat(log.getAttributes().size()).isEqualTo(4)
    assertThat(log.getAttributes().get(AttributeKey.stringKey("key1"))).isEqualTo("val1")
    assertThat(log.getAttributes().get(AttributeKey.stringKey("key2"))).isEqualTo("val2")
    assertThat(log.getAttributes().get(SemanticAttributes.THREAD_NAME)).isEqualTo(Thread.currentThread().getName())
    assertThat(log.getAttributes().get(SemanticAttributes.THREAD_ID)).isEqualTo(Thread.currentThread().getId())
  }
}
