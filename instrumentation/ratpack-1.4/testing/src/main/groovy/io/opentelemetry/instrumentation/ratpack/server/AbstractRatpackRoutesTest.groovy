/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.server

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.testing.internal.armeria.client.WebClient
import ratpack.path.PathBinding
import ratpack.server.RatpackServer
import ratpack.server.RatpackServerSpec
import spock.lang.Shared
import spock.lang.Unroll

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

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

  List<AttributeKey<?>> extraAttributes() {
    []
  }

  def "test bindings for #path"() {
    when:
    def resp = client.get(path).aggregate().join()

    then:
    resp.status().code() == 200
    resp.contentUtf8() == route

    def extraAttributes = extraAttributes()

    assertTraces(1) {
      trace(0, 1 + (hasHandlerSpan() ? 1 : 0)) {
        span(0) {
          name "/$route"
          kind SERVER
          hasNoParent()
          attributes {
            if (extraAttributes.contains(SemanticAttributes.NET_TRANSPORT)) {
              "${SemanticAttributes.NET_TRANSPORT}" IP_TCP
            }
            // net.peer.name resolves to "127.0.0.1" on windows which is same as net.peer.ip so then not captured
            "${SemanticAttributes.NET_PEER_NAME.key}" { it == null || it == "localhost" }
            "${SemanticAttributes.NET_PEER_IP.key}" { it == null || it == "127.0.0.1" }
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key}" String

            if (extraAttributes.contains(SemanticAttributes.HTTP_URL)) {
              "${SemanticAttributes.HTTP_URL.key}" "http://localhost:${app.bindPort}/${path}"
            } else {
              // TODO netty does not set http.scheme - refactor HTTP server tests so that it's possible to specify extracted attributes, like in HTTP client tests
              "${SemanticAttributes.HTTP_SCHEME}" { it == "http" || it == null }
              "${SemanticAttributes.HTTP_HOST}" "localhost:${app.bindPort}"
              "${SemanticAttributes.HTTP_TARGET}" "/$path"
            }

            if (extraAttributes.contains(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH)) {
              "${SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH}" Long
            }
            if (extraAttributes.contains(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH)) {
              "${SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH}" Long
            }
            if (extraAttributes.contains(SemanticAttributes.HTTP_ROUTE)) {
              // TODO(anuraaga): Revisit this when applying instrumenters to more libraries, Armeria
              // currently reports '/*' which is a fallback route.
              "${SemanticAttributes.HTTP_ROUTE}" String
            }
            if (extraAttributes.contains(SemanticAttributes.HTTP_SERVER_NAME)) {
              "${SemanticAttributes.HTTP_SERVER_NAME}" String
            }
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
