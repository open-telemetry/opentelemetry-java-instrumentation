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

package io.opentelemetry.instrumentation.armeria.v1_0

import static io.opentelemetry.trace.Span.Kind.CLIENT
import static io.opentelemetry.trace.Span.Kind.SERVER
import static org.junit.Assume.assumeTrue

import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.client.WebClientBuilder
import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.server.ServerBuilder
import com.linecorp.armeria.testing.junit4.server.ServerRule
import io.opentelemetry.auto.test.InstrumentationSpecification
import io.opentelemetry.trace.attributes.SemanticAttributes
import java.util.concurrent.CompletableFuture
import spock.lang.Shared
import spock.lang.Unroll

@Unroll
abstract class AbstractArmeriaTest extends InstrumentationSpecification {

  abstract ServerBuilder configureServer(ServerBuilder serverBuilder)

  abstract WebClientBuilder configureClient(WebClientBuilder clientBuilder)

  // We cannot annotate with @ClassRule since then Armeria will be class loaded before bytecode
  // instrumentation is set up by the Spock trait.
  @Shared
  protected ServerRule backend = new ServerRule() {
    @Override
    protected void configure(ServerBuilder sb) throws Exception {
      sb = configureServer(sb)
      sb.service("/hello", { ctx, req -> HttpResponse.of(HttpStatus.OK) })
    }
  }

  // We cannot annotate with @ClassRule since then Armeria will be class loaded before bytecode
  // instrumentation is set up by the Spock trait.
  @Shared
  protected ServerRule frontend = new ServerRule() {
    @Override
    protected void configure(ServerBuilder sb) throws Exception {
      sb = configureServer(sb)
      def backendClient = configureClient(WebClient.builder(backend.httpUri())).build()

      sb.service("/exact", { ctx, req -> HttpResponse.of(HttpStatus.OK) })
      sb.service("/items/{itemsId}", { ctx, req -> HttpResponse.of(HttpStatus.OK) })
      sb.service("/httperror", { ctx, req -> HttpResponse.of(HttpStatus.NOT_IMPLEMENTED) })
      sb.service("/exception", { ctx, req -> throw new IllegalStateException("illegal") })
      sb.service("/async", { ctx, req ->
        def executor = ctx.eventLoop()
        CompletableFuture<HttpResponse> resp = backendClient.get("/hello").aggregate(executor)
          .thenComposeAsync({ unused ->
            backendClient.get("/hello").aggregate()
          }, executor)
          .thenApplyAsync({ unused -> HttpResponse.of(HttpStatus.OK)}, executor)

        return HttpResponse.from(resp)
      })
    }
  }

  def client = configureClient(WebClient.builder(frontend.httpUri())).build()

  def "HTTP #method #path"() {
    when:
    def response = client.execute(HttpRequest.of(method, path)).aggregate().join()

    then:
    response.status().code() == code
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName("HTTP ${method}")
          spanKind CLIENT
          errored code != 200
          parent()
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            // TODO(anuraaga): peer name shouldn't be set to IP
            "${SemanticAttributes.NET_PEER_NAME.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
            "${SemanticAttributes.HTTP_URL.key()}" "${frontend.httpUri()}${path}"
            "${SemanticAttributes.HTTP_METHOD.key()}" method.name()
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" code
          }
        }
        span(1) {
          operationName(spanName)
          spanKind SERVER
          childOf span(0)
          errored code != 200
          if (path == "/exception") {
            errorEvent(IllegalStateException, "illegal")
          }
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
            "${SemanticAttributes.HTTP_URL.key()}" "${frontend.httpUri()}${path}"
            "${SemanticAttributes.HTTP_METHOD.key()}" method.name()
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" code
            "${SemanticAttributes.HTTP_FLAVOR.key()}" "h2c"
            "${SemanticAttributes.HTTP_USER_AGENT.key()}" String
            "${SemanticAttributes.HTTP_CLIENT_IP.key()}" "127.0.0.1"
          }
        }
      }
    }

    where:
    path          | spanName     | method          | code
    "/exact"      | "/exact"     | HttpMethod.GET  | 200
    // TODO(anuraaga): Seems to be an Armeria bug not to have :objectId here
    "/items/1234" | "/items/:"   | HttpMethod.POST | 200
    "/httperror"  | "/httperror" | HttpMethod.GET  | 501
    "/exception"  | "/exception" | HttpMethod.GET  | 500
  }

  def "context propagated by executor"() {
    when:
    assumeTrue(supportsContext())
    def response = client.get("/async").aggregate().join()

    then:
    response.status().code() == 200
    assertTraces(1) {
      trace(0, 6) {
        span(0) {
          operationName("HTTP GET")
          spanKind CLIENT
          parent()
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            // TODO(anuraaga): peer name shouldn't be set to IP
            "${SemanticAttributes.NET_PEER_NAME.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
            "${SemanticAttributes.HTTP_URL.key()}" "${frontend.httpUri()}/async"
            "${SemanticAttributes.HTTP_METHOD.key()}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" 200
          }
        }
        span(1) {
          operationName("/async")
          spanKind SERVER
          childOf span(0)
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
            "${SemanticAttributes.HTTP_URL.key()}" "${frontend.httpUri()}/async"
            "${SemanticAttributes.HTTP_METHOD.key()}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key()}" "h2c"
            "${SemanticAttributes.HTTP_USER_AGENT.key()}" String
            "${SemanticAttributes.HTTP_CLIENT_IP.key()}" "127.0.0.1"
          }
        }
        span(2) {
          operationName("HTTP GET")
          spanKind CLIENT
          childOf span(1)
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            // TODO(anuraaga): peer name shouldn't be set to IP
            "${SemanticAttributes.NET_PEER_NAME.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
            "${SemanticAttributes.HTTP_URL.key()}" "${backend.httpUri()}/hello"
            "${SemanticAttributes.HTTP_METHOD.key()}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" 200
          }
        }
        span(3) {
          operationName("/hello")
          spanKind SERVER
          childOf span(2)
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
            "${SemanticAttributes.HTTP_URL.key()}" "${backend.httpUri()}/hello"
            "${SemanticAttributes.HTTP_METHOD.key()}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key()}" "h2c"
            "${SemanticAttributes.HTTP_USER_AGENT.key()}" String
            "${SemanticAttributes.HTTP_CLIENT_IP.key()}" "127.0.0.1"
          }
        }
        span(4) {
          operationName("HTTP GET")
          spanKind CLIENT
          childOf span(1)
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            // TODO(anuraaga): peer name shouldn't be set to IP
            "${SemanticAttributes.NET_PEER_NAME.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
            "${SemanticAttributes.HTTP_URL.key()}" "${backend.httpUri()}/hello"
            "${SemanticAttributes.HTTP_METHOD.key()}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" 200
          }
        }
        span(5) {
          operationName("/hello")
          spanKind SERVER
          childOf span(4)
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
            "${SemanticAttributes.HTTP_URL.key()}" "${backend.httpUri()}/hello"
            "${SemanticAttributes.HTTP_METHOD.key()}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key()}" "h2c"
            "${SemanticAttributes.HTTP_USER_AGENT.key()}" String
            "${SemanticAttributes.HTTP_CLIENT_IP.key()}" "127.0.0.1"
          }
        }
      }
    }
  }

  boolean supportsContext() {
    return true
  }
}
