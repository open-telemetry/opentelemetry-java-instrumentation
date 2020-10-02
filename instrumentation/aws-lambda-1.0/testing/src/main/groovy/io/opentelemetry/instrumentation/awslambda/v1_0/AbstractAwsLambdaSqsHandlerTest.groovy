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
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import io.opentelemetry.OpenTelemetry
import io.opentelemetry.auto.test.InstrumentationSpecification
import io.opentelemetry.context.propagation.DefaultContextPropagators
import io.opentelemetry.extensions.trace.propagation.AwsXRayPropagator
import io.opentelemetry.trace.attributes.SemanticAttributes
import io.opentelemetry.trace.propagation.HttpTraceContext

abstract class AbstractAwsLambdaSqsHandlerTest extends InstrumentationSpecification {

  // Lambda instrumentation requires XRay propagator to be enabled.
  static {
    def propagators = DefaultContextPropagators.builder()
      .addTextMapPropagator(HttpTraceContext.instance)
      .addTextMapPropagator(AwsXRayPropagator.instance)
      .build()
    OpenTelemetry.setPropagators(propagators)
  }

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
