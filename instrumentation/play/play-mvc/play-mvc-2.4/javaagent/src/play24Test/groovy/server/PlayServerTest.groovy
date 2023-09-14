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
import io.opentelemetry.semconv.SemanticAttributes
import play.libs.F.Function0
import play.mvc.Results
import play.routing.RoutingDsl
import play.server.Server

import scala.Tuple2
import scala.collection.JavaConverters

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS
import static play.mvc.Http.Context.Implicit.request

class PlayServerTest extends HttpServerTest<Server> implements AgentTestTrait {
  @Override
  Server startServer(int port) {
    def router =
      new RoutingDsl()
        .GET(SUCCESS.getPath()).routeTo({
        controller(SUCCESS) {
          Results.status(SUCCESS.getStatus(), SUCCESS.getBody())
        }
      } as Function0)
        .GET(INDEXED_CHILD.getPath()).routeTo({
        controller(INDEXED_CHILD) {
          INDEXED_CHILD.collectSpanAttributes { request().getQueryString(it) }
          Results.status(INDEXED_CHILD.getStatus())
        }
      } as Function0)
        .GET(QUERY_PARAM.getPath()).routeTo({
        controller(QUERY_PARAM) {
          Results.status(QUERY_PARAM.getStatus(), QUERY_PARAM.getBody())
        }
      } as Function0)
        .GET(REDIRECT.getPath()).routeTo({
        controller(REDIRECT) {
          Results.found(REDIRECT.getBody())
        }
      } as play.libs.F.Function0)
        .GET(CAPTURE_HEADERS.getPath()).routeTo({
        controller(CAPTURE_HEADERS) {
          def javaResult = Results.status(CAPTURE_HEADERS.getStatus(), CAPTURE_HEADERS.getBody())
          def headers = Arrays.asList(new Tuple2<>("X-Test-Response", request().getHeader("X-Test-Request")))
          def scalaResult = javaResult.toScala().withHeaders(JavaConverters.asScalaIteratorConverter(headers.iterator()).asScala().toSeq())

          return new Results.Status(scalaResult)
        }
      } as Function0)
        .GET(ERROR.getPath()).routeTo({
        controller(ERROR) {
          Results.status(ERROR.getStatus(), ERROR.getBody())
        }
      } as Function0)
        .GET(EXCEPTION.getPath()).routeTo({
        controller(EXCEPTION) {
          throw new Exception(EXCEPTION.getBody())
        }
      } as Function0)

    return Server.forRouter(router.build(), port)
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

  @Override
  boolean verifyServerSpanEndTime() {
    // server spans are ended inside of the controller spans
    return false
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      name "play.request"
      kind INTERNAL
      if (endpoint == EXCEPTION) {
        status StatusCode.ERROR
        errorEvent(Exception, EXCEPTION.body)
      }
      childOf((SpanData) parent)
    }
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(ServerEndpoint endpoint) {
    def attributes = super.httpAttributes(endpoint)
    attributes.remove(SemanticAttributes.HTTP_ROUTE)
    attributes
  }
}
