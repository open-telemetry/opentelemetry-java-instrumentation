/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestServer
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import reactor.netty.http.client.HttpClient
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.api.trace.StatusCode.ERROR
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HttpFlavorValues.HTTP_1_1
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

class ReactorNettyConnectionSpanTest extends InstrumentationSpecification implements AgentTestTrait {

  @Shared
  private HttpClientTestServer server

  def setupSpec() {
    server = new HttpClientTestServer(openTelemetry)
    server.start()
  }

  def cleanupSpec() {
    server.stop()
  }

  def "test successful request"() {
    given:
    def httpClient = HttpClient.create()
    def uri = "http://localhost:${server.httpPort()}/success"

    when:
    def responseCode =
      runWithSpan("parent") {
        httpClient.get().uri(uri)
          .responseSingle { resp, content ->
            // Make sure to consume content since that's when we close the span.
            content.map { resp }
          }
          .block()
          .status().code()
      }

    then:
    responseCode == 200
    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        span(1) {
          name "HTTP GET"
          kind CLIENT
          childOf span(0)
          attributes {
            "${SemanticAttributes.HTTP_METHOD}" "GET"
            "${SemanticAttributes.HTTP_URL}" uri
            "${SemanticAttributes.HTTP_FLAVOR}" HTTP_1_1
            "${SemanticAttributes.HTTP_STATUS_CODE}" 200
            "${SemanticAttributes.NET_PEER_NAME.key}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key}" server.httpPort()
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
          }
        }
        span(2) {
          name "RESOLVE"
          kind INTERNAL
          childOf span(1)
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_NAME.key}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key}" server.httpPort()
          }
        }
        span(3) {
          name "CONNECT"
          kind INTERNAL
          childOf span(1)
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_NAME.key}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key}" server.httpPort()
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
          }
        }
        span(4) {
          name "test-http-server"
          kind SERVER
          childOf span(1)
        }
      }
    }
  }

  def "test failing request"() {
    given:
    def httpClient = HttpClient.create()
    def uri = "http://localhost:${PortUtils.UNUSABLE_PORT}"

    when:
    runWithSpan("parent") {
      httpClient.get().uri(uri)
        .responseSingle { resp, content ->
          // Make sure to consume content since that's when we close the span.
          content.map { resp }
        }
        .block()
        .status().code()
    }

    then:
    def thrownException = thrown(Exception)
    def connectException = thrownException.getCause()

    and:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
          status ERROR
          errorEvent(thrownException.class, thrownException.message)
        }
        span(1) {
          name "HTTP GET"
          kind CLIENT
          childOf span(0)
          status ERROR
          errorEvent(connectException.class, connectException.message)
          attributes {
            "${SemanticAttributes.HTTP_METHOD}" "GET"
            "${SemanticAttributes.HTTP_URL}" uri
          }
        }
        span(2) {
          name "RESOLVE"
          kind INTERNAL
          childOf span(1)
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_NAME.key}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key}" PortUtils.UNUSABLE_PORT
          }
        }
        span(3) {
          name "CONNECT"
          kind INTERNAL
          childOf span(1)
          status ERROR
          errorEvent(connectException.class, connectException.message)
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_NAME.key}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key}" PortUtils.UNUSABLE_PORT
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
          }
        }
      }
    }
  }
}
