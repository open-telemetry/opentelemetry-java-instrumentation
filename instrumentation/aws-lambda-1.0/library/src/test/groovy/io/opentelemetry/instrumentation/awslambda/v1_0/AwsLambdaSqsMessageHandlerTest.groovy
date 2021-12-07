/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import io.opentelemetry.instrumentation.test.LibraryInstrumentationSpecification
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.SERVER

class AwsLambdaSqsMessageHandlerTest extends LibraryInstrumentationSpecification {

  private static final String AWS_TRACE_HEADER1 = "Root=1-5759e988-bd862e3fe1be46a994272793;Parent=53995c3f42cd8ad8;Sampled=1"
  private static final String AWS_TRACE_HEADER2 = "Root=1-5759e988-bd862e3fe1be46a994272793;Parent=53995c3f42cd8ad9;Sampled=1"

  static class TestHandler extends TracingSqsMessageHandler {

    TestHandler(OpenTelemetrySdk openTelemetrySdk) {
      super(openTelemetrySdk)
    }

    @Override
    protected void handleMessage(SQSEvent.SQSMessage event, Context context) {
    }
  }

  def "messages with process spans"() {
    when:
    def context = Mock(Context)
    context.getFunctionName() >> "my_function"
    context.getAwsRequestId() >> "1-22-333"

    def message1 = new SQSEvent.SQSMessage()
    message1.setAttributes(["AWSTraceHeader": AWS_TRACE_HEADER1])
    message1.setMessageId("message1")
    message1.setEventSource("queue1")

    def message2 = new SQSEvent.SQSMessage()
    message2.setAttributes(["AWSTraceHeader": AWS_TRACE_HEADER2])
    message2.setMessageId("message2")
    message2.setEventSource("queue1")

    def event = new SQSEvent()
    event.setRecords([message1, message2])

    new TestHandler(testRunner().openTelemetrySdk).handleRequest(event, context)

    then:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name("my_function")
          kind SERVER
          attributes {
            "$SemanticAttributes.FAAS_EXECUTION" "1-22-333"
          }
        }
        span(1) {
          name("queue1 process")
          kind CONSUMER
          parentSpanId(span(0).spanId)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "AmazonSQS"
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
          }
          hasLink("5759e988bd862e3fe1be46a994272793", "53995c3f42cd8ad8")
          hasLink("5759e988bd862e3fe1be46a994272793", "53995c3f42cd8ad9")
        }
        span(2) {
          name("queue1 process")
          kind CONSUMER
          parentSpanId(span(1).spanId)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "AmazonSQS"
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" "message1"
            "$SemanticAttributes.MESSAGING_DESTINATION" "queue1"
          }
          hasLink("5759e988bd862e3fe1be46a994272793", "53995c3f42cd8ad8")
        }
        span(3) {
          name("queue1 process")
          kind CONSUMER
          parentSpanId(span(1).spanId)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "AmazonSQS"
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" "message2"
            "$SemanticAttributes.MESSAGING_DESTINATION" "queue1"
          }
          hasLink("5759e988bd862e3fe1be46a994272793", "53995c3f42cd8ad9")
        }
      }
    }
  }
}
