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

import static io.opentelemetry.trace.Span.Kind.CLIENT

import io.opentelemetry.auto.test.base.HttpClientTest
import io.opentelemetry.trace.attributes.SemanticAttributes
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.temporal.ChronoUnit
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Timeout

@Timeout(5)
abstract class JdkHttpClientTest extends HttpClientTest {

  @Shared
  def client = HttpClient.newBuilder().connectTimeout(Duration.of(CONNECT_TIMEOUT_MS,
    ChronoUnit.MILLIS)).followRedirects(HttpClient.Redirect.NORMAL).build()

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {

    def builder = HttpRequest.newBuilder().uri(uri).method(method, HttpRequest.BodyPublishers.noBody())

    headers.entrySet().each {
      builder.header(it.key, it.value)
    }
    def request = builder.build()

    def resp = send(request)
    callback?.call()
    return resp.statusCode()
  }

  abstract HttpResponse send(HttpRequest request)

  @Override
  boolean testCircularRedirects() {
    return false
  }

  //We override this test below because it produces somewhat different attributes
  @Override
  boolean testRemoteConnection() {
    return false
  }

  @Requires({ !System.getProperty("java.vm.name").contains("IBM J9 VM") })
  def "test https request"() {
    given:
    def uri = new URI("https://www.google.com/")

    when:
    def status = doRequest(method, uri)

    then:
    status == 200
    assertTraces(1) {
      trace(0, 1 + extraClientSpans()) {
        span(0) {
          parent()
          operationName expectedOperationName(method)
          spanKind CLIENT
          errored false
          attributes {
            "${SemanticAttributes.NET_PEER_NAME.key()}" uri.host
            "${SemanticAttributes.NET_PEER_IP.key()}" { it == null || it == "127.0.0.1" } // Optional
            "${SemanticAttributes.NET_PEER_PORT.key()}" uri.port > 0 ? uri.port : { it == null || it == 443 }
            "${SemanticAttributes.HTTP_URL.key()}" { it == "${uri}" || it == "${removeFragment(uri)}" }
            "${SemanticAttributes.HTTP_METHOD.key()}" method
            "${SemanticAttributes.HTTP_FLAVOR.key()}" "2.0"
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" status
          }
        }
      }
    }

    where:
    method = "HEAD"
  }

}
