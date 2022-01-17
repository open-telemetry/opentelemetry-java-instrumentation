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
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import play.BuiltInComponents
import play.Mode
import play.mvc.Controller
import play.mvc.Results
import play.routing.RoutingDsl
import play.server.Server

import java.util.function.Supplier

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class PlayServerTest extends HttpServerTest<Server> implements AgentTestTrait {
  @Override
  Server startServer(int port) {
    return Server.forRouter(Mode.TEST, port) { BuiltInComponents components ->
      RoutingDsl.fromComponents(components)
        .GET(SUCCESS.getPath()).routeTo({
        controller(SUCCESS) {
          Results.status(SUCCESS.getStatus(), SUCCESS.getBody())
        }
      } as Supplier)
        .GET(QUERY_PARAM.getPath()).routeTo({
        controller(QUERY_PARAM) {
          Results.status(QUERY_PARAM.getStatus(), QUERY_PARAM.getBody())
        }
      } as Supplier)
        .GET(REDIRECT.getPath()).routeTo({
        controller(REDIRECT) {
          Results.found(REDIRECT.getBody())
        }
      } as Supplier)
        .GET(ERROR.getPath()).routeTo({
        controller(ERROR) {
          Results.status(ERROR.getStatus(), ERROR.getBody())
        }
      } as Supplier)
        .GET(EXCEPTION.getPath()).routeTo({
        controller(EXCEPTION) {
          throw new Exception(EXCEPTION.getBody())
        }
      } as Supplier)
        .GET(CAPTURE_HEADERS.getPath()).routeTo({
        controller(CAPTURE_HEADERS) {
          Controller.request().header("X-Test-Request").ifPresent({ value ->
            Controller.response().setHeader("X-Test-Response", value)
          })
          Results.status(CAPTURE_HEADERS.getStatus(), CAPTURE_HEADERS.getBody())
        }
      } as Supplier)
        .GET(INDEXED_CHILD.getPath()).routeTo({
        controller(INDEXED_CHILD) {
          INDEXED_CHILD.collectSpanAttributes { name -> Controller.request().getQueryString(name) }
          Results.status(INDEXED_CHILD.getStatus(), INDEXED_CHILD.getBody())
        }
      } as Supplier)
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
    def attributes = super.httpAttributes(endpoint)
    attributes.remove(SemanticAttributes.HTTP_ROUTE)
    attributes
  }

  @Override
  String expectedServerSpanName(ServerEndpoint endpoint) {
    return "akka.request"
  }

}
