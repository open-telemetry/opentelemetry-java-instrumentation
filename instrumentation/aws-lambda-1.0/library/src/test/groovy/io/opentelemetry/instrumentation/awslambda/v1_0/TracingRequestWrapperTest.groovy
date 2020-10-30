/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0

import static io.opentelemetry.api.trace.Span.Kind.SERVER

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.InstrumentationTestTrait
import io.opentelemetry.api.trace.attributes.SemanticAttributes
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import spock.lang.Shared

class TracingRequestWrapperTest extends InstrumentationSpecification implements InstrumentationTestTrait {

  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

  static class TestRequestHandler implements RequestHandler<String, String> {

    @Override
    String handleRequest(String input, Context context) {
      if (input == "hello") {
        return "world"
      }
      throw new IllegalArgumentException("bad argument")
    }
  }

  @Shared
  TracingRequestWrapper wrapper

  def childSetup() {
    environmentVariables.set(WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY, "io.opentelemetry.instrumentation.awslambda.v1_0.TracingRequestWrapperTest\$TestRequestHandler::handleRequest")
    wrapper = new TracingRequestWrapper()
  }

  def "handler traced"() {
    when:
    def context = Mock(Context)
    context.getFunctionName() >> "my_function"
    context.getAwsRequestId() >> "1-22-333"

    def result = wrapper.handleRequest("hello", context)

    then:
    result == "world"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name("my_function")
          kind SERVER
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
      wrapper.handleRequest("goodbye", context)
    } catch (Throwable t) {
      thrown = t
    }

    then:
    thrown != null
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name("my_function")
          kind SERVER
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
