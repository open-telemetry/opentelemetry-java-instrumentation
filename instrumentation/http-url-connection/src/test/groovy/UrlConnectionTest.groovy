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

import static io.opentelemetry.auto.test.utils.PortUtils.UNUSABLE_PORT
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.trace.Span.Kind.CLIENT

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer
import io.opentelemetry.trace.attributes.SemanticAttributes

class UrlConnectionTest extends AgentTestRunner {

  def "trace request with connection failure #scheme"() {
    when:
    runUnderTrace("someTrace") {
      URLConnection connection = url.openConnection()
      connection.setConnectTimeout(10000)
      connection.setReadTimeout(10000)
      assert TEST_TRACER.getCurrentSpan() != null
      connection.inputStream
    }

    then:
    thrown ConnectException

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "someTrace"
          parent()
          errored true
          errorEvent ConnectException, String
        }
        span(1) {
          operationName expectedOperationName("GET")
          spanKind CLIENT
          childOf span(0)
          errored true
          errorEvent ConnectException, String
          attributes {
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key()}" UNUSABLE_PORT
            "${SemanticAttributes.HTTP_URL.key()}" "$url"
            "${SemanticAttributes.HTTP_METHOD.key()}" "GET"
            "${SemanticAttributes.HTTP_FLAVOR.key()}" "1.1"
          }
        }
      }
    }

    where:
    scheme << ["http", "https"]

    url = new URI("$scheme://localhost:$UNUSABLE_PORT").toURL()
  }

  String expectedOperationName(String method) {
    return method != null ? "HTTP $method" : HttpClientTracer.DEFAULT_SPAN_NAME
  }
}
