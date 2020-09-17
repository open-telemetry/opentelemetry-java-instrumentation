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
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.trace.attributes.SemanticAttributes
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.HttpClientResponse
import spock.lang.AutoCleanup
import spock.lang.Shared

import static io.opentelemetry.auto.test.server.http.TestHttpServer.httpServer
import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.trace.Span.Kind.CLIENT

class ReactorNettyTest extends AgentTestRunner {
  @AutoCleanup
  @Shared
  def server = httpServer {
    handlers {
      prefix("success") {
        response.status(200).send("Hello.")
      }
    }
  }

  private int doRequest() {
    HttpClientResponse resp = HttpClient.create()
      .baseUrl(server.address.toString())
      .get()
      .uri("/success")
      .response()
      .block()
    return resp.status().code()
  }

  def "two basic GET requests #url"() {
    when:
    runUnderTrace("parent") {
      doRequest()
    }
    runUnderTrace("parent") {
      doRequest()
    }

    then:
    assertTraces(2) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        clientSpan(it, 1, span(0))
      }
      trace(1, 2) {
        basicSpan(it, 0, "parent")
        clientSpan(it, 1, span(0))
      }
    }
  }

  void clientSpan(TraceAssert trace, int index, Object parentSpan, String method = "GET", URI uri = server.address.resolve("/success"), Integer status = 200) {
    def userAgent = "ReactorNetty/"
    trace.span(index) {
      if (parentSpan == null) {
        parent()
      } else {
        childOf((SpanData) parentSpan)
      }
      operationName "HTTP GET"
      spanKind CLIENT
      errored false
      attributes {
        "${SemanticAttributes.NET_PEER_NAME.key()}" uri.host
        "${SemanticAttributes.NET_PEER_IP.key()}" { it == null || it == "127.0.0.1" } // Optional
        "${SemanticAttributes.NET_PEER_PORT.key()}" uri.port > 0 ? uri.port : { it == null || it == 443 }
        "${SemanticAttributes.HTTP_URL.key()}" uri.toString()
        "${SemanticAttributes.HTTP_METHOD.key()}" method
        "${SemanticAttributes.HTTP_USER_AGENT.key()}" { it.startsWith(userAgent) }
        "${SemanticAttributes.HTTP_FLAVOR.key()}" "1.1"
        "${SemanticAttributes.HTTP_STATUS_CODE.key()}" status
      }
    }
  }

}
