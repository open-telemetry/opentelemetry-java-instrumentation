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

import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.server.ServerBuilder
import com.linecorp.armeria.testing.junit4.server.ServerRule
import io.opentelemetry.auto.test.InstrumentationSpecification
import io.opentelemetry.auto.test.utils.TraceUtils
import io.opentelemetry.trace.attributes.SemanticAttributes
import spock.lang.Ignore
import spock.lang.Shared

import static io.opentelemetry.trace.Span.Kind.SERVER

abstract class AbstractArmeriaServerTest extends InstrumentationSpecification {

  abstract void configureServer(ServerBuilder sb)

  // We cannot annotate with @ClassRule since then Armeria will be class loaded before bytecode
  // instrumentation is set up by the Spock trait.
  @Shared
  protected ServerRule server = new ServerRule() {
    @Override
    protected void configure(ServerBuilder sb) throws Exception {
      sb.service("/exact", { ctx, req -> HttpResponse.of(HttpStatus.OK) })
      sb.service("/items/{itemsId}", { ctx, req -> HttpResponse.of(HttpStatus.OK) })
      sb.service("/httperror", { ctx, req -> HttpResponse.of(HttpStatus.NOT_IMPLEMENTED) })
      sb.service("/exception", { ctx, req -> throw new IllegalStateException("illegal") })

      configureServer(sb)
    }
  }

  def client = WebClient.of(server.httpUri())

  def "HTTP #method #path"() {
    when:
    def response = client.execute(HttpRequest.of(method, path)).aggregate().join()

    then:
    response.status().code() == code
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName(spanName)
          spanKind SERVER
          errored code != 200
          if (path == "/exception") {
            errorEvent(IllegalStateException, "illegal")
          }
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
            "${SemanticAttributes.HTTP_URL.key()}" "${server.httpUri()}${path}"
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

  // TODO(anuraaga): Enable after instrumenting client.
  @Ignore
  def "extracts parent"() {
    when:
    def response
    TraceUtils.runUnderTrace("test") {
      response = client.execute(HttpRequest.of(method, path)).aggregate().join()
    }

    then:
    response.status().code() == code
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName(spanName)
          parent()
          spanKind SERVER
          errored false
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
            "${SemanticAttributes.HTTP_URL.key()}" "${server.httpUri()}${path}"
            "${SemanticAttributes.HTTP_METHOD.key()}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key()}" "HTTP/1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key()}" String
            "${SemanticAttributes.HTTP_CLIENT_IP.key()}" "127.0.0.1"
          }
        }
      }
    }

    where:
    path     | spanName | method         | code
    "/exact" | "/exact" | HttpMethod.GET | 200
  }
}
