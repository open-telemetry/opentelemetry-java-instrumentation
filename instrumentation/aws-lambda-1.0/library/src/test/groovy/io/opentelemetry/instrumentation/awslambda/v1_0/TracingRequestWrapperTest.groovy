/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.api.trace.StatusCode.ERROR

class TracingRequestWrapperTest extends TracingRequestWrapperTestBase {

  static class TestRequestHandlerString implements RequestHandler<String, String> {

    @Override
    String handleRequest(String input, Context context) {
      if (input == "hello") {
        return "world"
      }
      throw new IllegalArgumentException("bad argument")
    }
  }

  static class TestRequestHandlerInteger implements RequestHandler<Integer, String> {

    @Override
    String handleRequest(Integer input, Context context) {
      if (input == 1) {
        return "world"
      }
      throw new IllegalArgumentException("bad argument")
    }
  }

  static class CustomType {
    String key, value
  }

  static class TestRequestHandlerCustomType implements RequestHandler<CustomType, String> {

    @Override
    String handleRequest(CustomType input, Context context) {
      if (input.key == "hello there") {
        return input.value
      }
      throw new IllegalArgumentException("bad argument")
    }
  }

  def "handler string traced"() {
    given:
    setLambda(TestRequestHandlerString.getName() + "::handleRequest", TracingRequestWrapper.metaClass.&invokeConstructor, TracingRequestWrapper.&map)

    when:
    def result = wrapper.handleRequest("hello", context)

    then:
    result == "world"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name("my_function")
          kind SERVER
          attributes {
            "$ResourceAttributes.FAAS_ID.key" "arn:aws:lambda:us-east-1:123456789:function:test"
            "$ResourceAttributes.CLOUD_ACCOUNT_ID.key" "123456789"
            "${SemanticAttributes.FAAS_EXECUTION.key}" "1-22-333"
          }
        }
      }
    }
  }

  def "handler with exception"() {
    given:
    setLambda(TestRequestHandlerString.getName() + "::handleRequest", TracingRequestWrapper.metaClass.&invokeConstructor, TracingRequestWrapper.&map)

    when:
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
          status ERROR
          errorEvent(IllegalArgumentException, "bad argument")
          attributes {
            "$ResourceAttributes.FAAS_ID.key" "arn:aws:lambda:us-east-1:123456789:function:test"
            "$ResourceAttributes.CLOUD_ACCOUNT_ID.key" "123456789"
            "${SemanticAttributes.FAAS_EXECUTION.key}" "1-22-333"
          }
        }
      }
    }
  }

  def "handler integer traced"() {
    given:
    setLambda(TestRequestHandlerInteger.getName() + "::handleRequest", TracingRequestWrapper.metaClass.&invokeConstructor, TracingRequestWrapper.&map)

    when:
    def result = wrapper.handleRequest(1, context)

    then:
    result == "world"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name("my_function")
          kind SERVER
          attributes {
            "$ResourceAttributes.FAAS_ID.key" "arn:aws:lambda:us-east-1:123456789:function:test"
            "$ResourceAttributes.CLOUD_ACCOUNT_ID.key" "123456789"
            "${SemanticAttributes.FAAS_EXECUTION.key}" "1-22-333"
          }
        }
      }
    }
  }

  def "handler custom type traced"() {
    given:
    setLambda(TestRequestHandlerCustomType.getName() + "::handleRequest", TracingRequestWrapper.metaClass.&invokeConstructor, TracingRequestWrapper.&map)

    when:
    CustomType ct = new CustomType()
    ct.key = "hello there"
    ct.value = "General Kenobi"
    def result = wrapper.handleRequest(ct, context)

    then:
    result == "General Kenobi"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name("my_function")
          kind SERVER
          attributes {
            "$ResourceAttributes.FAAS_ID.key" "arn:aws:lambda:us-east-1:123456789:function:test"
            "$ResourceAttributes.CLOUD_ACCOUNT_ID.key" "123456789"
            "${SemanticAttributes.FAAS_EXECUTION.key}" "1-22-333"
          }
        }
      }
    }
  }
}
