/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0

import static io.opentelemetry.api.trace.Span.Kind.SERVER

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.google.common.collect.ImmutableMap
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.extension.trace.propagation.B3Propagator

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
      throw new RuntimeException("bad request")
    }
  }

  def childSetupSpec() {
    super.childSetupSpec()
    OpenTelemetry.setGlobalPropagators(ContextPropagators.create(B3Propagator.getInstance()))
  }

  def propagationHeaders() {
    return ImmutableMap.of("X-B3-TraceId", "4fd0b6131f19f39af59518d127b0cafe", "X-B3-SpanId", "0000000000000456", "X-B3-Sampled", "true")
  }

  def "handler traced with trace propagation"() {
    given:
    setLambda(TestApiGatewayHandler.getName() + "::handleRequest", TracingRequestApiGatewayWrapper)

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
            "$SemanticAttributes.FAAS_ID.key" "arn:aws:lambda:us-east-1:123456789:function:test"
            "$SemanticAttributes.CLOUD_ACCOUNT_ID.key" "123456789"
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
    setLambda(TestApiGatewayHandler.getName() + "::handleRequest", TracingRequestApiGatewayWrapper)

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
            "$SemanticAttributes.FAAS_ID.key" "arn:aws:lambda:us-east-1:123456789:function:test"
            "$SemanticAttributes.CLOUD_ACCOUNT_ID.key" "123456789"
            "$SemanticAttributes.FAAS_EXECUTION.key" "1-22-333"
            "$SemanticAttributes.FAAS_TRIGGER.key" "http"
          }
        }
      }
    }
  }
}
