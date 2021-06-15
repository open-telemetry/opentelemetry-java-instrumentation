/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.SERVER

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.testing.internal.armeria.client.WebClient
import ratpack.path.PathBinding
import ratpack.server.RatpackServer
import spock.lang.Shared

class RatpackOtherTest extends AgentInstrumentationSpecification {

  @Shared
  RatpackServer app = RatpackServer.start {
    it.handlers {
      it.prefix("a") {
        it.all {context ->
          context.render(context.get(PathBinding).description)
        }
      }
      it.prefix("b/::\\d+") {
        it.all {context ->
          context.render(context.get(PathBinding).description)
        }
      }
      it.prefix("c/:val?") {
        it.all {context ->
          context.render(context.get(PathBinding).description)
        }
      }
      it.prefix("d/:val") {
        it.all {context ->
          context.render(context.get(PathBinding).description)
        }
      }
      it.prefix("e/:val?:\\d+") {
        it.all {context ->
          context.render(context.get(PathBinding).description)
        }
      }
      it.prefix("f/:val:\\d+") {
        it.all {context ->
          context.render(context.get(PathBinding).description)
        }
      }
    }
  }

  // Force HTTP/1 with h1c to prevent tracing of upgrade request.
  @Shared
  WebClient client = WebClient.of("h1c://localhost:${app.bindPort}")

  def cleanupSpec() {
    app.stop()
  }

  def "test bindings for #path"() {
    when:
    def resp = client.get(path).aggregate().join()

    then:
    resp.status().code() == 200
    resp.contentUtf8() == route

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "/$route"
          kind SERVER
          hasNoParent()
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
            "${SemanticAttributes.HTTP_URL.key}" "http://localhost:${app.bindPort}/${path}"
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key}" String
            "${SemanticAttributes.HTTP_CLIENT_IP.key}" "127.0.0.1"
          }
        }
        span(1) {
          name "/$route"
          kind INTERNAL
          childOf span(0)
          attributes {
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
