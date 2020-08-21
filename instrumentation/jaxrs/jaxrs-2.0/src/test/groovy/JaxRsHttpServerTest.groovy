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

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static io.opentelemetry.trace.Span.Kind.INTERNAL
import static io.opentelemetry.trace.Span.Kind.SERVER

import io.dropwizard.jetty.NonblockingServletHolder
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.auto.test.base.HttpServerTest
import io.opentelemetry.instrumentation.api.MoreAttributes
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.trace.attributes.SemanticAttributes
import io.undertow.Undertow
import okhttp3.HttpUrl
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.servlet.ServletContainer
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer
import spock.lang.Unroll

abstract class JaxRsHttpServerTest<S> extends HttpServerTest<S> {
  @Unroll
  def "should handle #desc AsyncResponse"() {
    given:
    def url = HttpUrl.get(address.resolve("/async")).newBuilder()
      .addQueryParameter("action", action)
      .build()
    def request = request(url, "GET", null).build()

    when:
    def response = client.newCall(request).execute()

    then:
    assert response.code() == statusCode
    assert bodyPredicate(response.body().string())

    assertTraces(1) {
      trace(0, 2) {
        asyncServerSpan(it, 0, url, statusCode)
        handlerSpan(it, 1, span(0), "asyncOp", isCancelled, isError, errorMessage)
      }
    }

    where:
    desc         | action    | statusCode | bodyPredicate            | isCancelled | isError | errorMessage
    "successful" | "succeed" | 200        | { it == "success" }      | false       | false   | null
    "failing"    | "throw"   | 500        | { it == "failure" }      | false       | true    | "failure"
    "canceled"   | "cancel"  | 503        | { it instanceof String } | true        | false   | null
  }

  @Override
  boolean hasHandlerSpan() {
    true
  }

  @Override
  boolean testNotFound() {
    false
  }

  @Override
  boolean testPathParam() {
    true
  }

  @Override
  void serverSpan(TraceAssert trace,
                  int index,
                  String traceID = null,
                  String parentID = null,
                  String method = "GET",
                  Long responseContentLength = null,
                  ServerEndpoint endpoint = SUCCESS) {
    serverSpan(trace, index, traceID, parentID, method, responseContentLength,
      endpoint == PATH_PARAM ? "/path/{id}/param" : endpoint.resolvePath(address).path,
      endpoint.resolve(address),
      endpoint.errored,
      endpoint.status,
      endpoint.query)
  }

  void asyncServerSpan(TraceAssert trace,
                       int index,
                       HttpUrl url,
                       int statusCode) {
    def rawUrl = url.url()
    serverSpan(trace, index, null, null, "GET", null,
      rawUrl.path,
      rawUrl.toURI(),
      statusCode >= 500,
      statusCode,
      null)
  }

  void serverSpan(TraceAssert trace,
                  int index,
                  String traceID,
                  String parentID,
                  String method,
                  Long responseContentLength,
                  String path,
                  URI fullUrl,
                  boolean isError,
                  int statusCode,
                  String query) {
    trace.span(index) {
      operationName method + " /" + path
      spanKind SERVER
      errored isError
      if (parentID != null) {
        traceId traceID
        parentId parentID
      } else {
        parent()
      }
      attributes {
        "${SemanticAttributes.NET_PEER_IP.key()}" { it == null || it == "127.0.0.1" } // Optional
        "${SemanticAttributes.NET_PEER_PORT.key()}" Long
        "${SemanticAttributes.HTTP_URL.key()}" fullUrl.toString()
        "${SemanticAttributes.HTTP_METHOD.key()}" method
        "${SemanticAttributes.HTTP_STATUS_CODE.key()}" statusCode
        "${SemanticAttributes.HTTP_FLAVOR.key()}" "HTTP/1.1"
        "${SemanticAttributes.HTTP_USER_AGENT.key()}" TEST_USER_AGENT
        "${SemanticAttributes.HTTP_CLIENT_IP.key()}" TEST_CLIENT_IP
        if (responseContentLength) {
          "${SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH.key()}" responseContentLength
        } else {
          "${SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH.key()}" Long
        }
        "servlet.path" String
        "servlet.context" String
        if (query) {
          "$MoreAttributes.HTTP_QUERY" query
        }
      }
    }
  }

  @Override
  void handlerSpan(TraceAssert trace,
                   int index,
                   Object parent,
                   String method = "GET",
                   ServerEndpoint endpoint = SUCCESS) {
    handlerSpan(trace, index, parent,
      endpoint.name().toLowerCase(),
      false,
      endpoint == EXCEPTION,
      EXCEPTION.body)
  }

  void handlerSpan(TraceAssert trace,
                   int index,
                   Object parent,
                   String methodName,
                   boolean isCancelled,
                   boolean isError,
                   String exceptionMessage = null) {
    trace.span(index) {
      operationName "JaxRsTestResource.${methodName}"
      spanKind INTERNAL
      errored isError
      if (isError) {
        errorEvent(Exception, exceptionMessage)
      }
      childOf((SpanData) parent)
      attributes {
        if (isCancelled) {
          "canceled" true
        }
      }
    }
  }
}

class ResteasyHttpServerTest extends JaxRsHttpServerTest<UndertowJaxrsServer> {

  @Override
  UndertowJaxrsServer startServer(int port) {
    def server = new UndertowJaxrsServer()
    server.deploy(JaxRsTestApplication)
    server.start(Undertow.builder()
      .addHttpListener(port, "localhost"))
    return server
  }

  @Override
  void stopServer(UndertowJaxrsServer server) {
    server.stop()
  }
}

class JerseyHttpServerTest extends JaxRsHttpServerTest<Server> {

  @Override
  Server startServer(int port) {
    def servlet = new ServletContainer(ResourceConfig.forApplicationClass(JaxRsTestApplication))

    def handler = new ServletContextHandler(ServletContextHandler.SESSIONS)
    handler.setContextPath("/")
    handler.addServlet(new NonblockingServletHolder(servlet), "/*")

    def server = new Server(port)
    server.setHandler(handler)
    server.start()

    return server
  }

  @Override
  void stopServer(Server httpServer) {
    httpServer.stop()
  }
}