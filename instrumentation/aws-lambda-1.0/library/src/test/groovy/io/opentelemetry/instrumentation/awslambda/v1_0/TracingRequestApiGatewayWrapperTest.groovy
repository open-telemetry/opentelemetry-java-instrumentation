/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.google.common.collect.ImmutableMap
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

import static io.opentelemetry.api.trace.SpanKind.SERVER

class TracingRequestApiGatewayWrapperTest extends TracingRequestWrapperTestBase {

  static class TestApiGatewayHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
      if (input.getBody() == "hello") {
        return new APIGatewayProxyResponseEvent()
          .withStatusCode(200)
          .withBody("world")
      } else if (input.getBody() == "empty") {
        return new APIGatewayProxyResponseEvent()
      }
      throw new IllegalStateException("bad request")
    }
  }

  def propagationHeaders() {
    return Collections.singletonMap("traceparent", "00-4fd0b6131f19f39af59518d127b0cafe-0000000000000456-01")
  }

  def "handler traced with trace propagation"() {
    given:
    setLambda(TestApiGatewayHandler.getName() + "::handleRequest", TracingRequestApiGatewayWrapper.metaClass.&invokeConstructor)

    def headers = ImmutableMap.builder()
      .putAll(propagationHeaders())
      .put("User-Agent", "Test Client")
      .put("host", "localhost:123")
      .put("X-FORWARDED-PROTO", "http")
      .build()
    def input = new APIGatewayProxyRequestEvent()
      .withHttpMethod("GET")
      .withResource("/hello/{param}")
      .withPath("/hello/world")
      .withBody("hello")
      .withQueryStringParamters(["a": "b", "c": "d"])
      .withHeaders(headers)

    when:
    APIGatewayProxyResponseEvent result = wrapper.handleRequest(input, context)

    then:
    result.getBody() == "world"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          parentSpanId("0000000000000456")
          traceId("4fd0b6131f19f39af59518d127b0cafe")
          name("/hello/{param}")
          kind SERVER
          attributes {
            "$ResourceAttributes.FAAS_ID.key" "arn:aws:lambda:us-east-1:123456789:function:test"
            "$ResourceAttributes.CLOUD_ACCOUNT_ID.key" "123456789"
            "$SemanticAttributes.FAAS_EXECUTION.key" "1-22-333"
            "$SemanticAttributes.FAAS_TRIGGER.key" "http"
            "$SemanticAttributes.HTTP_METHOD.key" "GET"
            "$SemanticAttributes.HTTP_USER_AGENT.key" "Test Client"
            "$SemanticAttributes.HTTP_URL.key" "http://localhost:123/hello/world?a=b&c=d"
            "$SemanticAttributes.HTTP_STATUS_CODE.key" 200
          }
        }
      }
    }
  }

  def "test empty request & response"() {
    given:
    setLambda(TestApiGatewayHandler.getName() + "::handleRequest", TracingRequestApiGatewayWrapper.metaClass.&invokeConstructor)

    def input = new APIGatewayProxyRequestEvent()
      .withBody("empty")

    when:
    APIGatewayProxyResponseEvent result = wrapper.handleRequest(input, context)

    then:
    result.body == null
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name("my_function")
          kind SERVER
          attributes {
            "$ResourceAttributes.FAAS_ID.key" "arn:aws:lambda:us-east-1:123456789:function:test"
            "$ResourceAttributes.CLOUD_ACCOUNT_ID.key" "123456789"
            "$SemanticAttributes.FAAS_EXECUTION.key" "1-22-333"
            "$SemanticAttributes.FAAS_TRIGGER.key" "http"
          }
        }
      }
    }
  }
}
