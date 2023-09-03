/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import io.opentelemetry.semconv.SemanticAttributes
import org.glassfish.grizzly.http.server.HttpHandler
import org.glassfish.grizzly.http.server.HttpServer
import org.glassfish.grizzly.http.server.NetworkListener
import org.glassfish.grizzly.http.server.Request
import org.glassfish.grizzly.http.server.Response
import org.glassfish.grizzly.http.server.ServerConfiguration

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS

class GrizzlyTest extends HttpServerTest<HttpServer> implements AgentTestTrait {

  @Override
  HttpServer startServer(int port) {
    HttpServer server = new HttpServer()
    NetworkListener listener = new NetworkListener("grizzly", "localhost", port)
    configureListener(listener)
    server.addListener(listener)
    ServerConfiguration config = server.getServerConfiguration()
    config.addHttpHandler(new HttpHandler() {
      @Override
      void service(Request request, Response response) throws Exception {
        def endpoint = ServerEndpoint.forPath(request.getDecodedRequestURI())
        controller(endpoint) {
          switch (endpoint) {
            case SUCCESS:
              response.status = endpoint.status
              response.writer.write(endpoint.body)
              break
            case INDEXED_CHILD:
              response.status = endpoint.status
              endpoint.collectSpanAttributes { request.getParameter(it) }
              break
            case QUERY_PARAM:
              response.status = endpoint.status
              response.writer.write(request.queryString)
              break
            case REDIRECT:
              response.sendRedirect(endpoint.body)
              break
            case ERROR:
              response.sendError(endpoint.status, endpoint.body)
              break
            case NOT_FOUND:
              response.status = endpoint.status
              break
            case EXCEPTION:
              throw new Exception(EXCEPTION.body)
            default:
              throw new IllegalStateException("unexpected endpoint " + endpoint)
          }
        }
      }
    }, "/")

    server.start()

    return server
  }

  void configureListener(NetworkListener listener) {
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(ServerEndpoint endpoint) {
    def attributes = super.httpAttributes(endpoint)
    attributes.remove(SemanticAttributes.HTTP_ROUTE)
    attributes.remove(SemanticAttributes.NET_TRANSPORT)
    attributes
  }

  @Override
  void stopServer(HttpServer server) {
    server.stop()
  }

  @Override
  boolean hasResponseCustomizer(ServerEndpoint endpoint) {
    true
  }

  @Override
  boolean testCapturedHttpHeaders() {
    false
  }

  @Override
  boolean verifyServerSpanEndTime() {
    // fails for redirect test
    false
  }

  @Override
  boolean testErrorBody() {
    false
  }
}
