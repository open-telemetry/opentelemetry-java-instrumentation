/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

import play.libs.F.Function0
import play.mvc.Results
import play.routing.RoutingDsl
import play.server.Server

import scala.Tuple2
import scala.collection.JavaConverters

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS
import static play.libs.F.Promise.promise
import static play.mvc.Http.Context.Implicit.request

class PlayAsyncServerTest extends PlayServerTest {
  @Override
  Server startServer(int port) {
    def router =
      new RoutingDsl()
        .GET(SUCCESS.getPath()).routeAsync({
        promise({
          controller(SUCCESS) {
            Results.status(SUCCESS.getStatus(), SUCCESS.getBody())
          }
        })
      } as Function0)
        .GET(INDEXED_CHILD.getPath()).routeTo({
        promise({
          controller(INDEXED_CHILD) {
            INDEXED_CHILD.collectSpanAttributes { request().getQueryString(it) }
            Results.status(INDEXED_CHILD.getStatus())
          }
        })
      } as Function0)
        .GET(QUERY_PARAM.getPath()).routeAsync({
        promise({
          controller(QUERY_PARAM) {
            Results.status(QUERY_PARAM.getStatus(), QUERY_PARAM.getBody())
          }
        })
      } as Function0)
        .GET(REDIRECT.getPath()).routeAsync({
        promise({
          controller(REDIRECT) {
            Results.found(REDIRECT.getBody())
          }
        })
      } as Function0)
        .GET(CAPTURE_HEADERS.getPath()).routeAsync({
        promise({
          controller(CAPTURE_HEADERS) {
            def javaResult = Results.status(CAPTURE_HEADERS.getStatus(), CAPTURE_HEADERS.getBody())
            def headers = Arrays.asList(new Tuple2<>("X-Test-Response", request().getHeader("X-Test-Request")))
            def scalaResult = javaResult.toScala().withHeaders(JavaConverters.asScalaIteratorConverter(headers.iterator()).asScala().toSeq())

            return new Results.Status(scalaResult)
          }
        })
      } as Function0)
        .GET(ERROR.getPath()).routeAsync({
        promise({
          controller(ERROR) {
            Results.status(ERROR.getStatus(), ERROR.getBody())
          }
        })
      } as Function0)
        .GET(EXCEPTION.getPath()).routeAsync({
        promise({
          controller(EXCEPTION) {
            throw new Exception(EXCEPTION.getBody())
          }
        })
      } as Function0)

    return Server.forRouter(router.build(), port)
  }
}
