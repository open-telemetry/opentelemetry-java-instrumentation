/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package server

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static io.opentelemetry.trace.Span.Kind.INTERNAL

import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.auto.test.base.HttpServerTest
import io.opentelemetry.sdk.trace.data.SpanData
import play.mvc.Results
import play.routing.RoutingDsl
import play.server.Server
import play.libs.F

class PlayServerTest extends HttpServerTest<Server> {
  @Override
  Server startServer(int port) {
    def router =
      new RoutingDsl()
        .GET(SUCCESS.getPath()).routeTo(
        new F.Function0<Results.Status>() {
          @Override
          Results.Status apply() throws Throwable {
            return controller(SUCCESS) {
              Results.status(SUCCESS.getStatus(), SUCCESS.getBody())
            }
          }})
        .GET(QUERY_PARAM.getPath()).routeTo(
        new F.Function0<Results.Status>() {
          @Override
          Results.Status apply() throws Throwable {
            return controller(QUERY_PARAM) {
              Results.status(QUERY_PARAM.getStatus(), QUERY_PARAM.getBody())
            }
          }
        })
        .GET(REDIRECT.getPath()).routeTo(
        new F.Function0<Results.Status>() {
          @Override
          Results.Status apply() throws Throwable {
            return controller(REDIRECT) {
              Results.found(REDIRECT.getBody())
            }
          }
        })
        .GET(ERROR.getPath()).routeTo(
        new F.Function0<Results.Status>() {
          @Override
          Results.Status apply() throws Throwable {
            return controller(ERROR) {
              Results.status(ERROR.getStatus(), ERROR.getBody())
            }
          }
        })
        .GET(EXCEPTION.getPath()).routeTo(
        new F.Function0<Results.Status>() {
          @Override
          Results.Status apply() throws Throwable {
            return controller(EXCEPTION) {
              throw new Exception(EXCEPTION.getBody())
            }
          }
        })
    return Server.forRouter(router.build(), port)
  }

  @Override
  void stopServer(Server server) {
    server.stop()
  }

  @Override
  boolean hasHandlerSpan() {
    true
  }

  boolean testExceptionBody() {
    // I can't figure out how to set a proper exception handler to customize the response body.
    false
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      operationName "play.request"
      spanKind INTERNAL
      errored endpoint == EXCEPTION
      if (endpoint == EXCEPTION) {
        errorEvent(Exception, EXCEPTION.body)
      }
      childOf((SpanData) parent)
    }
  }

  @Override
  String expectedServerSpanName(String method, ServerEndpoint endpoint) {
    return "netty.request"
  }

}
