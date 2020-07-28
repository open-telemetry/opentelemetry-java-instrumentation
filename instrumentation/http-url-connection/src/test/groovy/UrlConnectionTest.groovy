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

import io.opentelemetry.auto.bootstrap.AgentClassLoader
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpClientDecorator
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.trace.attributes.SemanticAttributes

import static io.opentelemetry.auto.test.utils.PortUtils.UNUSABLE_PORT
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.trace.Span.Kind.CLIENT

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
            "${SemanticAttributes.HTTP_URL.key()}" "$url/"
            "${SemanticAttributes.HTTP_METHOD.key()}" "GET"
          }
        }
      }
    }

    where:
    scheme << ["http", "https"]

    url = new URI("$scheme://localhost:$UNUSABLE_PORT").toURL()
  }

  def "trace request with connection failure to a local file with broken url path"() {
    setup:
    def url = new URI("file:/some-random-file%abc").toURL()

    when:
    runUnderTrace("someTrace") {
      url.openConnection()
    }

    then:
    thrown IllegalArgumentException

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "someTrace"
          parent()
          errored true
          errorEvent IllegalArgumentException, String
        }
        span(1) {
          operationName "file.request"
          spanKind CLIENT
          childOf span(0)
          errored true
          errorEvent IllegalArgumentException, String
          attributes {
            "${SemanticAttributes.NET_PEER_PORT.key()}" 80
            // FIXME: These attributes really make no sense for non-http connections, why do we set them?
            "${SemanticAttributes.HTTP_URL.key()}" "$url"
          }
        }
      }
    }
  }

  def "AgentClassloader ClassNotFoundException doesn't create span"() {
    given:
    ClassLoader agentLoader = new AgentClassLoader(null, null, null)
    ClassLoader childLoader = new URLClassLoader(new URL[0], agentLoader)

    when:
    runUnderTrace("someTrace") {
      childLoader.loadClass("io.opentelemetry.auto.doesnotexist")
    }

    then:
    thrown ClassNotFoundException

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "someTrace"
          parent()
          errored true
          errorEvent ClassNotFoundException, String
        }
      }
    }
  }

  String expectedOperationName(String method) {
    return method != null ? "HTTP $method" : HttpClientDecorator.DEFAULT_SPAN_NAME
  }
}
