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
import io.opentelemetry.api.trace.attributes.SemanticAttributes
import io.opentelemetry.context.propagation.DefaultContextPropagators
import io.opentelemetry.extension.trace.propagation.B3Propagator
import io.opentelemetry.instrumentation.test.AgentTestRunner

class TracingRequestApiGatewayWrapperTest extends TracingRequestWrapperTestBase {

  static class TestApiGatewayHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
      if (input.getBody() == "hello") {
        return new APIGatewayProxyResponseEvent().withBody("world")
      }
      throw new RuntimeException("bad request")
    }
  }

  def childSetupSpec() {
    super.childSetupSpec()
    AgentTestRunner.setGlobalPropagators(DefaultContextPropagators.builder()
      .addTextMapPropagator(B3Propagator.getInstance()).build())
  }

  def propagationHeaders() {
    return ImmutableMap.of("X-B3-TraceId", "4fd0b6131f19f39af59518d127b0cafe", "X-B3-SpanId", "0000000000000456", "X-B3-Sampled", "true")
  }

  def "handler traced with trace propagation"() {
    given:
    setLambda(TestApiGatewayHandler.getName()+"::handleRequest", TracingRequestApiGatewayWrapper)
    def input = new APIGatewayProxyRequestEvent().withBody("hello").withHeaders(propagationHeaders())

    when:
    APIGatewayProxyResponseEvent result = wrapper.handleRequest(input, context)

    then:
    result.getBody() == "world"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          parentSpanId("0000000000000456")
          traceId("4fd0b6131f19f39af59518d127b0cafe")
          name("my_function")
          kind SERVER
          attributes {
            "${SemanticAttributes.FAAS_EXECUTION.key}" "1-22-333"
          }
        }
      }
    }
  }
}
