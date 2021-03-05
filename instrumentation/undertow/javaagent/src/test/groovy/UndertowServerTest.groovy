/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.undertow.Handlers
import io.undertow.Undertow
import io.undertow.util.Headers
import io.undertow.util.StatusCodes

//TODO make test which mixes handlers and servlets
class UndertowServerTest extends HttpServerTest<Undertow> implements AgentTestTrait {

  @Override
  Undertow startServer(int port) {
    Undertow server = Undertow.builder()
      .addHttpListener(port, "localhost")
      .setHandler(Handlers.path()
        .addExactPath(SUCCESS.rawPath()) { exchange ->
          controller(SUCCESS) {
            exchange.getResponseSender().send(SUCCESS.body)
          }
        }
        .addExactPath(QUERY_PARAM.rawPath()) { exchange ->
          controller(QUERY_PARAM) {
            exchange.getResponseSender().send(exchange.getQueryString())
          }
        }
        .addExactPath(REDIRECT.rawPath()) { exchange ->
          controller(REDIRECT) {
            exchange.setStatusCode(StatusCodes.FOUND)
            exchange.getResponseHeaders().put(Headers.LOCATION, REDIRECT.body)
            exchange.endExchange()
          }
        }
        .addExactPath(ERROR.rawPath()) { exchange ->
          controller(ERROR) {
            exchange.setStatusCode(ERROR.status)
            exchange.getResponseSender().send(ERROR.body)
          }
        }
        .addExactPath(EXCEPTION.rawPath()) { exchange ->
          controller(EXCEPTION) {
            throw new Exception(EXCEPTION.body)
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
  String expectedServerSpanName(ServerEndpoint endpoint) {
    return "HTTP GET"
  }
}
