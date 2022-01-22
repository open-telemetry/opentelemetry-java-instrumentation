/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.undertow.Handlers
import io.undertow.Undertow
import io.undertow.util.Headers
import io.undertow.util.HttpString
import io.undertow.util.StatusCodes

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class UndertowServerDispatchTest extends HttpServerTest<Undertow> implements AgentTestTrait {

  @Override
  boolean verifyServerSpanEndTime() {
    return false
  }

  @Override
  boolean testException() {
    // throwing exception from dispatched task just makes the request time out
    return false
  }

  @Override
  Undertow startServer(int port) {
    Undertow server = Undertow.builder()
      .addHttpListener(port, "localhost")
      .setHandler(Handlers.path()
        .addExactPath(SUCCESS.rawPath()) { exchange ->
          exchange.dispatch {
            controller(SUCCESS) {
              exchange.getResponseSender().send(SUCCESS.body)
            }
          }
        }
        .addExactPath(QUERY_PARAM.rawPath()) { exchange ->
          exchange.dispatch {
            controller(QUERY_PARAM) {
              exchange.getResponseSender().send(exchange.getQueryString())
            }
          }
        }
        .addExactPath(REDIRECT.rawPath()) { exchange ->
          exchange.dispatch {
            controller(REDIRECT) {
              exchange.setStatusCode(StatusCodes.FOUND)
              exchange.getResponseHeaders().put(Headers.LOCATION, REDIRECT.body)
              exchange.endExchange()
            }
          }
        }
        .addExactPath(CAPTURE_HEADERS.rawPath()) { exchange ->
          exchange.dispatch {
            controller(CAPTURE_HEADERS) {
              exchange.setStatusCode(StatusCodes.OK)
              exchange.getResponseHeaders().put(new HttpString("X-Test-Response"), exchange.getRequestHeaders().getFirst("X-Test-Request"))
              exchange.getResponseSender().send(CAPTURE_HEADERS.body)
            }
          }
        }
        .addExactPath(ERROR.rawPath()) { exchange ->
          exchange.dispatch {
            controller(ERROR) {
              exchange.setStatusCode(ERROR.status)
              exchange.getResponseSender().send(ERROR.body)
            }
          }
        }
        .addExactPath(INDEXED_CHILD.rawPath()) { exchange ->
          exchange.dispatch {
            controller(INDEXED_CHILD) {
              INDEXED_CHILD.collectSpanAttributes { name -> exchange.getQueryParameters().get(name).peekFirst() }
              exchange.getResponseSender().send(INDEXED_CHILD.body)
            }
          }
        }
      ).build()
    server.start()
    return server
  }

  @Override
  void stopServer(Undertow undertow) {
    undertow.stop()
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(ServerEndpoint endpoint) {
    def attributes = super.httpAttributes(endpoint)
    attributes.remove(SemanticAttributes.HTTP_ROUTE)
    attributes.add(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH)
    attributes
  }
}
