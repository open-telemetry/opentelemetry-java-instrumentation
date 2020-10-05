/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0

import static io.opentelemetry.trace.Span.Kind.SERVER

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import io.opentelemetry.auto.test.InstrumentationSpecification
import io.opentelemetry.trace.attributes.SemanticAttributes

abstract class AbstractAwsLambdaRequestHandlerTest extends InstrumentationSpecification {

  protected static String doHandleRequest(String input, Context context) {
    if (input == "hello") {
      return "world"
    }
    throw new IllegalArgumentException("bad argument")
  }

  abstract RequestHandler<String, String> handler()

  def "handler traced"() {
    when:
    def context = Mock(Context)
    context.getFunctionName() >> "my_function"
    context.getAwsRequestId() >> "1-22-333"

    def result = handler().handleRequest("hello", context)

    then:
    result == "world"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName("my_function")
          spanKind SERVER
          attributes {
            "${SemanticAttributes.FAAS_EXECUTION.key}" "1-22-333"
          }
        }
      }
    }
  }

  def "handler traced with exception"() {
    when:
    def context = Mock(Context)
    context.getFunctionName() >> "my_function"
    context.getAwsRequestId() >> "1-22-333"

    def thrown
    try {
      handler().handleRequest("goodbye", context)
    } catch (Throwable t) {
      thrown = t
    }

    then:
    thrown != null
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName("my_function")
          spanKind SERVER
          errored true
          errorEvent(IllegalArgumentException, "bad argument")
          attributes {
            "${SemanticAttributes.FAAS_EXECUTION.key}" "1-22-333"
          }
        }
      }
    }
  }
}
