/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse
import io.undertow.Handlers
import io.undertow.Undertow
import io.undertow.util.Headers
import io.undertow.util.StatusCodes

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

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
        .addExactPath("sendResponse") { exchange ->
          Span.current().addEvent("before-event")
          runWithSpan("sendResponse") {
            exchange.setStatusCode(StatusCodes.OK)
            exchange.getResponseSender().send("sendResponse")
          }
          // event is added only when server span has not been ended
          // we need to make sure that sending response does not end server span
          Span.current().addEvent("after-event")
        }
        .addExactPath("sendResponseWithException") { exchange ->
          Span.current().addEvent("before-event")
          runWithSpan("sendResponseWithException") {
            exchange.setStatusCode(StatusCodes.OK)
            exchange.getResponseSender().send("sendResponseWithException")
          }
          // event is added only when server span has not been ended
          // we need to make sure that sending response does not end server span
          Span.current().addEvent("after-event")
          throw new Exception("exception after sending response")
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

  @Override
  List<AttributeKey<?>> extraAttributes() {
    [
      SemanticAttributes.HTTP_HOST,
      SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
      SemanticAttributes.HTTP_SCHEME,
      SemanticAttributes.HTTP_TARGET,
      SemanticAttributes.NET_PEER_NAME,
      SemanticAttributes.NET_TRANSPORT
    ]
  }

  def "test send response"() {
    setup:
    def uri = address.resolve("sendResponse")
    AggregatedHttpResponse response = client.get(uri.toString()).aggregate().join()

    expect:
    response.status().code() == 200
    response.contentUtf8().trim() == "sendResponse"

    and:
    assertTraces(1) {
      trace(0, 2) {
        it.span(0) {
          hasNoParent()
          name "HTTP GET"
          kind SpanKind.SERVER

          event(0) {
            eventName "before-event"
          }
          event(1) {
            eventName "after-event"
          }

          attributes {
            "${SemanticAttributes.NET_PEER_PORT.key}" { it instanceof Long }
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.HTTP_CLIENT_IP.key}" TEST_CLIENT_IP
            "${SemanticAttributes.HTTP_SCHEME.key}" uri.getScheme()
            "${SemanticAttributes.HTTP_HOST.key}" uri.getHost() + ":" + uri.getPort()
            "${SemanticAttributes.HTTP_TARGET.key}" uri.getPath()
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key}" TEST_USER_AGENT
            "${SemanticAttributes.HTTP_HOST}" "localhost:${port}"
            "${SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH}" Long
            "${SemanticAttributes.HTTP_SCHEME}" "http"
            "${SemanticAttributes.HTTP_TARGET}" "/sendResponse"
            // "localhost" on linux, "127.0.0.1" on windows
            "${SemanticAttributes.NET_PEER_NAME.key}" { it == "localhost" || it == "127.0.0.1" }
            "${SemanticAttributes.NET_TRANSPORT}" SemanticAttributes.NetTransportValues.IP_TCP
          }
        }
        span(1) {
          name "sendResponse"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }
  }

  def "test send response with exception"() {
    setup:
    def uri = address.resolve("sendResponseWithException")
    AggregatedHttpResponse response = client.get(uri.toString()).aggregate().join()

    expect:
    response.status().code() == 200
    response.contentUtf8().trim() == "sendResponseWithException"

    and:
    assertTraces(1) {
      trace(0, 2) {
        it.span(0) {
          hasNoParent()
          name "HTTP GET"
          kind SpanKind.SERVER
          status StatusCode.ERROR

          event(0) {
            eventName "before-event"
          }
          event(1) {
            eventName "after-event"
          }
          errorEvent(Exception, "exception after sending response", 2)

          attributes {
            "${SemanticAttributes.NET_PEER_PORT.key}" { it instanceof Long }
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.HTTP_CLIENT_IP.key}" TEST_CLIENT_IP
            "${SemanticAttributes.HTTP_SCHEME.key}" uri.getScheme()
            "${SemanticAttributes.HTTP_HOST.key}" uri.getHost() + ":" + uri.getPort()
            "${SemanticAttributes.HTTP_TARGET.key}" uri.getPath()
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key}" TEST_USER_AGENT
            "${SemanticAttributes.HTTP_HOST}" "localhost:${port}"
            "${SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH}" Long
            "${SemanticAttributes.HTTP_SCHEME}" "http"
            "${SemanticAttributes.HTTP_TARGET}" "/sendResponseWithException"
            // "localhost" on linux, "127.0.0.1" on windows
            "${SemanticAttributes.NET_PEER_NAME.key}" { it == "localhost" || it == "127.0.0.1" }
            "${SemanticAttributes.NET_TRANSPORT}" SemanticAttributes.NetTransportValues.IP_TCP
          }
        }
        span(1) {
          name "sendResponseWithException"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }
  }
}
