/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.SERVER

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.instrumentation.test.InstrumentationSpecification

abstract class AbstractAwsLambdaSqsHandlerTest extends InstrumentationSpecification {

  private static final String AWS_TRACE_HEADER = "Root=1-5759e988-bd862e3fe1be46a994272793;Parent=53995c3f42cd8ad8;Sampled=1"

  abstract RequestHandler<SQSEvent, Void> handler()

  def "messages from same source"() {
    when:
    def context = Mock(Context)
    context.getFunctionName() >> "my_function"
    context.getAwsRequestId() >> "1-22-333"

    def message1 = new SQSEvent.SQSMessage()
    message1.setAttributes(["AWSTraceHeader": AWS_TRACE_HEADER])
    message1.setMessageId("message1")
    message1.setEventSource("queue1")

    def message2 = new SQSEvent.SQSMessage()
    message2.setAttributes([:])
    message2.setMessageId("message2")
    message2.setEventSource("queue1")

    def event = new SQSEvent()
    event.setRecords([message1, message2])

    handler().handleRequest(event, context)

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name("my_function")
          kind SERVER
          attributes {
            "${SemanticAttributes.FAAS_EXECUTION.key}" "1-22-333"
          }
        }
        span(1) {
          name("queue1 process")
          kind CONSUMER
          parentSpanId(span(0).spanId)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "AmazonSQS"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
          }
          hasLink("5759e988bd862e3fe1be46a994272793", "53995c3f42cd8ad8")
        }
      }
    }
  }

  def "messages from different source"() {
    when:
    def context = Mock(Context)
    context.getFunctionName() >> "my_function"
    context.getAwsRequestId() >> "1-22-333"

    def message1 = new SQSEvent.SQSMessage()
    message1.setAttributes(["AWSTraceHeader": AWS_TRACE_HEADER])
    message1.setMessageId("message1")
    message1.setEventSource("queue1")

    def message2 = new SQSEvent.SQSMessage()
    message2.setAttributes([:])
    message2.setMessageId("message2")
    message2.setEventSource("queue2")

    def event = new SQSEvent()
    event.setRecords([message1, message2])

    handler().handleRequest(event, context)

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name("my_function")
          kind SERVER
          attributes {
            "${SemanticAttributes.FAAS_EXECUTION.key}" "1-22-333"
          }
        }
        span(1) {
          name("multiple_sources process")
          kind CONSUMER
          parentSpanId(span(0).spanId)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "AmazonSQS"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
          }
          hasLink("5759e988bd862e3fe1be46a994272793", "53995c3f42cd8ad8")
        }
      }
    }
  }
}
