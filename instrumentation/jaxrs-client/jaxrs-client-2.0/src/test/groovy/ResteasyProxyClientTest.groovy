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

import static io.opentelemetry.auto.test.server.http.TestHttpServer.httpServer
import static io.opentelemetry.trace.Span.Kind.CLIENT

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.trace.attributes.SemanticAttributes
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Unroll

import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Response

class ResteasyProxyClientTest extends AgentTestRunner {
  @AutoCleanup
  @Shared
  def server = httpServer {
    handlers {
      prefix("hello") {
        response.status(200)
          .send("hello from proxy", "text/plain")
      }
    }
  }

  @Unroll
  def "should call HTTP through Resteasy proxy (#desc)"() {
    given:
    def client = new ResteasyClientBuilder().build()
    def target = client.target(server.address)
    def proxy = ((ResteasyWebTarget) target).proxy(HttpProxyResource)

    when:
    def response = proxyMethod(proxy)

    then:
    assert getResponseBody(response) == "hello from proxy"

    assertTraces(1) {
      trace(0, 1) {
        clientSpan(it, 0, null, path)
      }
    }

    where:
    desc                     | proxyMethod           | getResponseBody           | path
    "proxy returns Response" | { it.response() }     | { it.readEntity(String) } | "/hello-response"
    "proxy returns String"   | { it.responseBody() } | { it }                    | "/hello-body"
  }

  void clientSpan(TraceAssert trace, int index, Object parentSpan,
                  String path,
                  String method = "GET",
                  Integer status = 200) {
    URI uri = server.address.resolve(path)
    trace.span(index) {
      if (parentSpan == null) {
        parent()
      } else {
        childOf((SpanData) parentSpan)
      }
      operationName "HTTP ${method}"
      spanKind CLIENT
      errored false
      attributes {
        "${SemanticAttributes.NET_PEER_NAME.key()}" uri.host
        "${SemanticAttributes.NET_PEER_IP.key()}" { it == null || it == "127.0.0.1" } // Optional
        "${SemanticAttributes.NET_PEER_PORT.key()}" uri.port > 0 ? uri.port : { it == null || it == 443 }
        "${SemanticAttributes.HTTP_URL.key()}" "${uri}"
        "${SemanticAttributes.HTTP_METHOD.key()}" method
        "${SemanticAttributes.HTTP_STATUS_CODE.key()}" status
      }
    }
  }
}

@Path("")
interface HttpProxyResource {
  @GET
  @Path("hello-response")
  @Produces("text/plain")
  Response response()

  @GET
  @Path("hello-body")
  @Produces("text/plain")
  String responseBody()
}