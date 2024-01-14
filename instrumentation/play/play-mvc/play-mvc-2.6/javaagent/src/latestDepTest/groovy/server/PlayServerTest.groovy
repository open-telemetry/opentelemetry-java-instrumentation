/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import io.opentelemetry.sdk.trace.data.SpanData
import play.BuiltInComponents
import play.Mode
import play.mvc.Results
import play.routing.RequestFunctions
import play.routing.RoutingDsl
import play.server.Server

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS

class PlayServerTest extends HttpServerTest<Server> implements AgentTestTrait {
  @Override
  Server startServer(int port) {
    return Server.forRouter(Mode.TEST, port) { BuiltInComponents components ->
      RoutingDsl.fromComponents(components)
        .GET(SUCCESS.getPath()).routingTo({
        controller(SUCCESS) {
          Results.status(SUCCESS.getStatus(), SUCCESS.getBody())
        }
      } as RequestFunctions.Params0)
        .GET(QUERY_PARAM.getPath()).routingTo({
        controller(QUERY_PARAM) {
          Results.status(QUERY_PARAM.getStatus(), QUERY_PARAM.getBody())
        }
      } as RequestFunctions.Params0)
        .GET(REDIRECT.getPath()).routingTo({
        controller(REDIRECT) {
          Results.found(REDIRECT.getBody())
        }
      } as RequestFunctions.Params0)
        .GET(ERROR.getPath()).routingTo({
        controller(ERROR) {
          Results.status(ERROR.getStatus(), ERROR.getBody())
        }
      } as RequestFunctions.Params0)
        .GET(EXCEPTION.getPath()).routingTo({
        controller(EXCEPTION) {
          throw new Exception(EXCEPTION.getBody())
        }
      } as RequestFunctions.Params0)
        .GET(CAPTURE_HEADERS.getPath()).routingTo({request ->
        controller(CAPTURE_HEADERS) {
          def result = Results.status(CAPTURE_HEADERS.getStatus(), CAPTURE_HEADERS.getBody())
          request.header("X-Test-Request").ifPresent({ value ->
            result = result.withHeader("X-Test-Response", value)
          })
          result
        }
      } as RequestFunctions.Params0)
        .GET(INDEXED_CHILD.getPath()).routingTo({request ->
        controller(INDEXED_CHILD) {
          INDEXED_CHILD.collectSpanAttributes { name -> request.queryString(name).get() }
          Results.status(INDEXED_CHILD.getStatus(), INDEXED_CHILD.getBody())
        }
      } as RequestFunctions.Params0)
        .build()
    }
  }

  @Override
  void stopServer(Server server) {
    server.stop()
  }

  @Override
  boolean hasHandlerSpan(ServerEndpoint endpoint) {
    true
  }

  @Override
  boolean testHttpPipelining() {
    false
  }

  // play does not emit a span at all when a non standard HTTP method is used
  @Override
  boolean testNonStandardHttpMethod() {
    false
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      name "play.request"
      kind INTERNAL
      childOf((SpanData) parent)
      if (endpoint == EXCEPTION) {
        status StatusCode.ERROR
        errorEvent(Exception, EXCEPTION.body)
      }
    }
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(ServerEndpoint endpoint) {
    []
  }
}
