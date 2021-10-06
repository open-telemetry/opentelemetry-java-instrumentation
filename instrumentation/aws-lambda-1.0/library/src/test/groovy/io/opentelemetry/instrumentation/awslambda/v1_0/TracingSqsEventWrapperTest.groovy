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
import io.opentelemetry.instrumentation.test.LibraryInstrumentationSpecification
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables

class TracingSqsEventWrapperTest extends LibraryInstrumentationSpecification {

  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

  TracingSqsEventWrapper wrapper
  Context context

  def setup() {
    context = Mock(Context)
    context.getFunctionName() >> "my_function"
    context.getAwsRequestId() >> "1-22-333"
    context.getInvokedFunctionArn() >> "arn:aws:lambda:us-east-1:123456789:function:test"
  }

  def setLambda(handler, Closure<TracingSqsEventWrapper> wrapperConstructor) {
    environmentVariables.set(WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY, handler)
    wrapper = wrapperConstructor.call(testRunner().openTelemetrySdk, WrappedLambda.fromConfiguration())
  }

  static class TestRequestHandler implements RequestHandler<SQSEvent, Void> {

    @Override
    Void handleRequest(SQSEvent input, Context context) {
      return null
    }

  }


  def "handler event traced"() {
    given:
    setLambda(TestRequestHandler.getName() + "::handleRequest", TracingSqsEventWrapper.metaClass.&invokeConstructor)

    when:
    SQSEvent event = new SQSEvent()
    SQSEvent.SQSMessage record = new SQSEvent.SQSMessage()
    record.setEventSource("otel")
    record.setAttributes(Collections.emptyMap())
    event.setRecords(Arrays.asList(record))
    wrapper.handleRequest(event, context)

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name("my_function")
          kind SERVER
          attributes {
            "$ResourceAttributes.FAAS_ID.key" "arn:aws:lambda:us-east-1:123456789:function:test"
            "$ResourceAttributes.CLOUD_ACCOUNT_ID.key" "123456789"
            "${SemanticAttributes.FAAS_EXECUTION.key}" "1-22-333"
          }
        }
        span(1) {
          name("otel process")
          kind CONSUMER
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM}" "AmazonSQS"
            "${SemanticAttributes.MESSAGING_OPERATION}" "process"
          }
        }
      }
    }
  }
}
