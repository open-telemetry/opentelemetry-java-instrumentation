/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.server

import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.ServerAttributes
import io.opentelemetry.semconv.ClientAttributes
import io.opentelemetry.semconv.UserAgentAttributes
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.NetworkAttributes
import io.opentelemetry.semconv.UrlAttributes
import io.opentelemetry.testing.internal.armeria.client.WebClient
import ratpack.path.PathBinding
import ratpack.server.RatpackServer
import ratpack.server.RatpackServerSpec
import spock.lang.Shared
import spock.lang.Unroll

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.SERVER

@Unroll
abstract class AbstractRatpackRoutesTest extends InstrumentationSpecification {

  abstract void configure(RatpackServerSpec serverSpec)

  @Shared
  RatpackServer app

  // Force HTTP/1 with h1c to prevent tracing of upgrade request.
  @Shared
  WebClient client

  def setupSpec() {
    app = RatpackServer.start {
      it.serverConfig {
        it.port(PortUtils.findOpenPort())
        it.address(InetAddress.getByName("localhost"))
      }
      it.handlers {
        it.prefix("a") {
          it.all { context ->
            context.render(context.get(PathBinding).description)
          }
        }
        it.prefix("b/::\\d+") {
          it.all { context ->
            context.render(context.get(PathBinding).description)
          }
        }
        it.prefix("c/:val?") {
          it.all { context ->
            context.render(context.get(PathBinding).description)
          }
        }
        it.prefix("d/:val") {
          it.all { context ->
            context.render(context.get(PathBinding).description)
          }
        }
        it.prefix("e/:val?:\\d+") {
          it.all { context ->
            context.render(context.get(PathBinding).description)
          }
        }
        it.prefix("f/:val:\\d+") {
          it.all { context ->
            context.render(context.get(PathBinding).description)
          }
        }
      }
      configure(it)
    }
    client = WebClient.of("h1c://localhost:${app.bindPort}")
  }

  def cleanupSpec() {
    app.stop()
  }

  abstract boolean hasHandlerSpan()

  def "test bindings for #path"() {
    when:
    def resp = client.get(path).aggregate().join()

    then:
    resp.status().code() == 200
    resp.contentUtf8() == route

    assertTraces(1) {
      trace(0, 1 + (hasHandlerSpan() ? 1 : 0)) {
        span(0) {
          name "GET /$route"
          kind SERVER
          hasNoParent()
          attributes {
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_ADDRESS" { it == "localhost" || it == null }
            "$ServerAttributes.SERVER_PORT" { it == app.bindPort || it == null }
            "$ClientAttributes.CLIENT_ADDRESS" { it == "127.0.0.1" || it == null }
            "$NetworkAttributes.NETWORK_PEER_ADDRESS" { it == "127.0.0.1" || it == null }
            "$NetworkAttributes.NETWORK_PEER_PORT" { it instanceof Long || it == null }
            "$HttpAttributes.HTTP_REQUEST_METHOD" "GET"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UserAgentAttributes.USER_AGENT_ORIGINAL" String
            "$UrlAttributes.URL_SCHEME" "http"
            "$UrlAttributes.URL_PATH" "/$path"
            "$UrlAttributes.URL_QUERY" { it == "" || it == null }
            "$HttpAttributes.HTTP_ROUTE" "/$route"
          }
        }
        if (hasHandlerSpan()) {
          span(1) {
            name "/$route"
            kind INTERNAL
            childOf span(0)
            attributes {
            }
          }
        }
      }
    }

    where:
    path    | route
    "a"     | "a"
    "b/123" | "b/::\\d+"
    "c"     | "c/:val?"
    "c/123" | "c/:val?"
    "c/foo" | "c/:val?"
    "d/123" | "d/:val"
    "d/foo" | "d/:val"
    "e"     | "e/:val?:\\d+"
    "e/123" | "e/:val?:\\d+"
    "e/foo" | "e/:val?:\\d+"
    "f/123" | "f/:val:\\d+"
  }
}
