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
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse
import io.undertow.Handlers
import io.undertow.Undertow
import io.undertow.util.Headers
import io.undertow.util.HttpString
import io.undertow.util.StatusCodes

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS

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
        .addExactPath(CAPTURE_HEADERS.rawPath()) { exchange ->
          controller(CAPTURE_HEADERS) {
            exchange.setStatusCode(StatusCodes.OK)
            exchange.getResponseHeaders().put(new HttpString("X-Test-Response"), exchange.getRequestHeaders().getFirst("X-Test-Request"))
            exchange.getResponseSender().send(CAPTURE_HEADERS.body)
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
        .addExactPath(INDEXED_CHILD.rawPath()) { exchange ->
          controller(INDEXED_CHILD) {
            INDEXED_CHILD.collectSpanAttributes { name -> exchange.getQueryParameters().get(name).peekFirst() }
            exchange.getResponseSender().send(INDEXED_CHILD.body)
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
  Set<AttributeKey<?>> httpAttributes(ServerEndpoint endpoint) {
    def attributes = super.httpAttributes(endpoint)
    attributes.remove(SemanticAttributes.HTTP_ROUTE)
    attributes.add(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH)
    attributes
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
            "$SemanticAttributes.NET_PEER_PORT" { it instanceof Long }
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.HTTP_CLIENT_IP" TEST_CLIENT_IP
            "$SemanticAttributes.HTTP_SCHEME" uri.getScheme()
            "$SemanticAttributes.HTTP_HOST" uri.getHost() + ":" + uri.getPort()
            "$SemanticAttributes.HTTP_TARGET" uri.getPath()
            "$SemanticAttributes.HTTP_METHOD" "GET"
            "$SemanticAttributes.HTTP_STATUS_CODE" 200
            "$SemanticAttributes.HTTP_FLAVOR" "1.1"
            "$SemanticAttributes.HTTP_USER_AGENT" TEST_USER_AGENT
            "$SemanticAttributes.HTTP_HOST" "localhost:${port}"
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" Long
            "$SemanticAttributes.HTTP_SCHEME" "http"
            "$SemanticAttributes.HTTP_TARGET" "/sendResponse"
            // net.peer.name resolves to "127.0.0.1" on windows which is same as net.peer.ip so then not captured
            "$SemanticAttributes.NET_PEER_NAME" { it == "localhost" || it == null }
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
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
            "$SemanticAttributes.NET_PEER_PORT" { it instanceof Long }
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.HTTP_CLIENT_IP" TEST_CLIENT_IP
            "$SemanticAttributes.HTTP_SCHEME" uri.getScheme()
            "$SemanticAttributes.HTTP_HOST" uri.getHost() + ":" + uri.getPort()
            "$SemanticAttributes.HTTP_TARGET" uri.getPath()
            "$SemanticAttributes.HTTP_METHOD" "GET"
            "$SemanticAttributes.HTTP_STATUS_CODE" 200
            "$SemanticAttributes.HTTP_FLAVOR" "1.1"
            "$SemanticAttributes.HTTP_USER_AGENT" TEST_USER_AGENT
            "$SemanticAttributes.HTTP_HOST" "localhost:${port}"
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" Long
            "$SemanticAttributes.HTTP_SCHEME" "http"
            "$SemanticAttributes.HTTP_TARGET" "/sendResponseWithException"
            // net.peer.name resolves to "127.0.0.1" on windows which is same as net.peer.ip so then not captured
            "$SemanticAttributes.NET_PEER_NAME" { it == "localhost" || it == null }
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
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
