/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.awslambda.v1_0

import static io.opentelemetry.trace.Span.Kind.CONSUMER
import static io.opentelemetry.trace.Span.Kind.SERVER

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import io.opentelemetry.auto.test.InstrumentationSpecification
import io.opentelemetry.auto.test.InstrumentationTestTrait
import io.opentelemetry.trace.attributes.SemanticAttributes

class AwsLambdaSqsMessageHandlerTest extends InstrumentationSpecification implements InstrumentationTestTrait {

  private static final String AWS_TRACE_HEADER1 = "Root=1-5759e988-bd862e3fe1be46a994272793;Parent=53995c3f42cd8ad8;Sampled=1"
  private static final String AWS_TRACE_HEADER2 = "Root=1-5759e988-bd862e3fe1be46a994272793;Parent=53995c3f42cd8ad9;Sampled=1"

  class TestHandler extends TracingSQSMessageHandler {
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

    new TestHandler().handleRequest(event, context)

    then:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          operationName("my_function")
          spanKind SERVER
          attributes {
            "${SemanticAttributes.FAAS_EXECUTION}" "1-22-333"
          }
        }
        span(1) {
          operationName("queue1 receive")
          spanKind CONSUMER
          parentId(span(0).spanId)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM}" "AmazonSQS"
            "${SemanticAttributes.MESSAGING_OPERATION}" "receive"
          }
          hasLink("5759e988bd862e3fe1be46a994272793", "53995c3f42cd8ad8")
          hasLink("5759e988bd862e3fe1be46a994272793", "53995c3f42cd8ad9")
        }
        span(2) {
          operationName("queue1 process")
          spanKind CONSUMER
          parentId(span(1).spanId)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM}" "AmazonSQS"
            "${SemanticAttributes.MESSAGING_OPERATION}" "process"
            "${SemanticAttributes.MESSAGING_MESSAGE_ID}" "message1"
            "${SemanticAttributes.MESSAGING_DESTINATION}" "queue1"
          }
          hasLink("5759e988bd862e3fe1be46a994272793", "53995c3f42cd8ad8")
        }
        span(3) {
          operationName("queue1 process")
          spanKind CONSUMER
          parentId(span(1).spanId)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM}" "AmazonSQS"
            "${SemanticAttributes.MESSAGING_OPERATION}" "process"
            "${SemanticAttributes.MESSAGING_MESSAGE_ID}" "message2"
            "${SemanticAttributes.MESSAGING_DESTINATION}" "queue1"
          }
          hasLink("5759e988bd862e3fe1be46a994272793", "53995c3f42cd8ad9")
        }
      }
    }
  }
}
