/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes
import listener.AnnotatedListenerConfig
import listener.ManualListenerConfig
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.jms.core.JmsTemplate

import javax.jms.ConnectionFactory

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

class SpringListenerTest extends AgentInstrumentationSpecification {
  def "receiving message in spring listener generates spans"() {
    setup:
    def context = new AnnotationConfigApplicationContext(config)
    def factory = context.getBean(ConnectionFactory)
    def template = new JmsTemplate(factory)

    template.convertAndSend("SpringListenerJms2", "a message")

    def producer
    expect:
    assertTraces(2) {
      traces.sort(orderByRootSpanKind(PRODUCER, CONSUMER))

      trace(0, 1) {
        producerSpan(it, 0, "SpringListenerJms2")
        producer = span(0)
      }
      trace(1, 2) {
        consumerSpan(it, 0, "SpringListenerJms2", "", null, producer, "receive")
        consumerSpan(it, 1, "SpringListenerJms2", "", span(0), producer, "process")
      }
    }

    cleanup:
    context.close()

    where:
    config << [AnnotatedListenerConfig, ManualListenerConfig]
  }

  static producerSpan(TraceAssert trace, int index, String destinationName, boolean testHeaders = false) {
    trace.span(index) {
      name destinationName + " publish"
      kind PRODUCER
      hasNoParent()
      attributes {
        "$MessagingIncubatingAttributes.MESSAGING_SYSTEM" "jms"
        "$MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME" destinationName
        "$MessagingIncubatingAttributes.MESSAGING_OPERATION" "publish"
        if (destinationName == "(temporary)") {
          "$MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPORARY" true
        }
        "$MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID" String
        if (testHeaders) {
          "messaging.header.test_message_header" { it == ["test"] }
          "messaging.header.test_message_int_header" { it == ["1234"] }
        }
      }
    }
  }

  // passing messageId = null will verify message.id is not captured,
  // passing messageId = "" will verify message.id is captured (but won't verify anything about the value),
  // any other value for messageId will verify that message.id is captured and has that same value
  static consumerSpan(TraceAssert trace, int index, String destinationName, String messageId, Object parent, Object linkedSpan, String operation, boolean testHeaders = false) {
    trace.span(index) {
      name destinationName + " " + operation
      kind CONSUMER
      if (parent != null) {
        childOf((SpanData) parent)
      } else {
        hasNoParent()
      }
      if (linkedSpan != null) {
        hasLink((SpanData) linkedSpan)
      } else {
        hasNoLinks()
      }
      attributes {
        "$MessagingIncubatingAttributes.MESSAGING_SYSTEM" "jms"
        "$MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME" destinationName
        "$MessagingIncubatingAttributes.MESSAGING_OPERATION" operation
        if (messageId != null) {
          //In some tests we don't know exact messageId, so we pass "" and verify just the existence of the attribute
          "$MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID" { it == messageId || messageId == "" }
        }
        if (destinationName == "(temporary)") {
          "$MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPORARY" true
        }
        if (testHeaders) {
          "messaging.header.test_message_header" { it == ["test"] }
          "messaging.header.test_message_int_header" { it == ["1234"] }
        }
      }
    }
  }
}
