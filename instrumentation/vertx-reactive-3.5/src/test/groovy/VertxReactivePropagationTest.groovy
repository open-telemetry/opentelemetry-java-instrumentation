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

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.OkHttpUtils
import io.opentelemetry.auto.test.utils.PortUtils
import io.opentelemetry.trace.attributes.SemanticAttributes
import io.vertx.reactivex.core.Vertx
import okhttp3.OkHttpClient
import okhttp3.Request
import spock.lang.Shared

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.trace.Span.Kind.CLIENT
import static io.opentelemetry.trace.Span.Kind.SERVER

class VertxReactivePropagationTest extends AgentTestRunner {
  @Shared
  OkHttpClient client = OkHttpUtils.client()

  @Shared
  int port

  @Shared
  Vertx server

  def setupSpec() {
    port = PortUtils.randomOpenPort()
    server = VertxReactiveWebServer.start(port)
  }

  def cleanupSpec() {
    server.close()
  }

  //Verifies that context is correctly propagated and sql query span has correct parent.
  //Tests io.opentelemetry.auto.instrumentation.vertx.reactive.VertxRxInstrumentation
  def "should propagate context over vert.x rx-java framework"() {
    setup:
    def url = "http://localhost:$port/listProducts"
    def request = new Request.Builder().url(url).get().build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == SUCCESS.status

    and:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          operationName "/listProducts"
          spanKind SERVER
          errored false
          parent()
          attributes {
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
            "${SemanticAttributes.NET_PEER_IP.key()}" { it == null || it == "127.0.0.1" } // Optional
            "${SemanticAttributes.HTTP_URL.key()}" url
            "${SemanticAttributes.HTTP_METHOD.key()}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" 200
          }
        }
        basicSpan(it, 1, "VertxReactiveWebServer.handleListProducts", span(0))
        basicSpan(it, 2, "VertxReactiveWebServer.listProducts", span(1))
        span(3) {
          operationName "SELECT id, name, price, weight FROM products"
          spanKind CLIENT
          childOf span(2)
          errored false
          attributes {
            "${SemanticAttributes.DB_TYPE.key()}" "sql"
            "${SemanticAttributes.DB_INSTANCE.key()}" "test?shutdown=true"
            "${SemanticAttributes.DB_USER.key()}" "SA"
            "${SemanticAttributes.DB_STATEMENT.key()}" "SELECT id, name, price, weight FROM products"
            "${SemanticAttributes.DB_URL.key()}" "hsqldb:mem:"
            "span.origin.type" String
          }
        }
      }
    }
  }


}
