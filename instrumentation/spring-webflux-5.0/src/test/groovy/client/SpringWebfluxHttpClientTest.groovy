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

package client

import static io.opentelemetry.trace.Span.Kind.CLIENT

import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.auto.test.base.HttpClientTest
import io.opentelemetry.instrumentation.api.MoreAttributes
import io.opentelemetry.trace.attributes.SemanticAttributes
import org.springframework.http.HttpMethod
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import spock.lang.Timeout

@Timeout(5)
class SpringWebfluxHttpClientTest extends HttpClientTest {

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    ClientResponse response = WebClient.builder().build().method(HttpMethod.resolve(method))
      .uri(uri)
      .headers { h -> headers.forEach({ key, value -> h.add(key, value) }) }
      .exchange()
      .doAfterSuccessOrError { res, ex ->
        callback?.call()
      }
      .block()

    response.statusCode().value()
  }

  @Override
  // parent spanRef must be cast otherwise it breaks debugging classloading (junit loads it early)
  void clientSpan(TraceAssert trace, int index, Object parentSpan, String method = "GET", boolean tagQueryString = false, URI uri = server.address.resolve("/success"), Integer status = 200, Throwable exception = null) {
    super.clientSpan(trace, index, parentSpan, method, tagQueryString, uri, status, exception)
    if (!exception) {
      trace.span(index + 1) {
        childOf(trace.span(index))
        operationName "HTTP $method"
        spanKind CLIENT
        errored exception != null
        if (exception) {
          errorEvent(exception.class, exception.message)
        }
        attributes {
          "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
          "${SemanticAttributes.NET_PEER_PORT.key()}" uri.port
          "${SemanticAttributes.NET_PEER_IP.key()}" { it == null || it == "127.0.0.1" } // Optional
          "${SemanticAttributes.HTTP_URL.key()}" { it == "${uri}" || it == "${removeFragment(uri)}" }
          "${SemanticAttributes.HTTP_METHOD.key()}" method
          "${SemanticAttributes.HTTP_USER_AGENT.key()}" { it.startsWith("ReactorNetty") }
          if (status) {
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" status
          }
          if (tagQueryString) {
            "$MoreAttributes.HTTP_QUERY" uri.query
            "$MoreAttributes.HTTP_FRAGMENT" { it == null || it == uri.fragment } // Optional
          }
        }
      }
    }
  }

  @Override
  int extraClientSpans() {
    // has netty-client span inside of spring-webflux-client
    return 1
  }

  boolean testRedirects() {
    false
  }

  boolean testConnectionFailure() {
    false
  }


  boolean testRemoteConnection() {
    // FIXME: figure out how to configure timeouts.
    false
  }
}
