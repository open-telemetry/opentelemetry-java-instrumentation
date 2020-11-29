/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test

import static io.opentelemetry.api.trace.Span.Kind.CLIENT
import static io.opentelemetry.api.trace.Span.Kind.INTERNAL
import static io.opentelemetry.api.trace.Span.Kind.SERVER

import com.google.common.collect.ImmutableMap
import io.opentelemetry.api.trace.attributes.SemanticAttributes
import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.instrumentation.test.utils.PortUtils
import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Shared

class RestCamelTest extends AgentTestRunner {

  @Shared
  ConfigurableApplicationContext server
  @Shared
  int port

  def setupSpec() {
    withRetryOnAddressAlreadyInUse({
      setupSpecUnderRetry()
    })
  }

  def setupSpecUnderRetry() {
    port = PortUtils.randomOpenPort()
    def app = new SpringApplication(RestConfig)
    app.setDefaultProperties(ImmutableMap.of("restServer.port", port))
    server = app.run()
    println getClass().name + " http server started at: http://localhost:$port/"
  }

  def cleanupSpec() {
    if (server != null) {
      server.close()
      server = null
    }
  }

  def "rest component - server and client call with jetty backend"() {
    setup:
    def camelContext = server.getBean(CamelContext)
    ProducerTemplate template = camelContext.createProducerTemplate()

    when:
    // run client and server in separate threads to simulate "real" rest client/server call
    new Thread(new Runnable() {
      @Override
      void run() {
        template.sendBodyAndHeaders("direct:start", null, ImmutableMap.of("module", "firstModule", "unitId", "unitOne"))
      }
    }
    ).start()

    then:
    assertTraces(1) {
      trace(0, 5) {
        it.span(0) {
          name "start"
          kind INTERNAL
          attributes {
            "camel.uri" "direct://start"
          }
        }
        it.span(1) {
          name "GET"
          kind CLIENT
          attributes {
            "$SemanticAttributes.HTTP_METHOD.key" "GET"
            "$SemanticAttributes.HTTP_STATUS_CODE.key" 200
            "camel.uri" "rest://get:api/%7Bmodule%7D/unit/%7BunitId%7D"
          }
        }
        it.span(2) {
          name "/api/{module}/unit/{unitId}"
          kind SERVER
          attributes {
            "$SemanticAttributes.HTTP_URL.key" "http://localhost:$port/api/firstModule/unit/unitOne"
            "$SemanticAttributes.HTTP_STATUS_CODE.key" 200
            "$SemanticAttributes.HTTP_CLIENT_IP.key" "127.0.0.1"
            "$SemanticAttributes.HTTP_USER_AGENT.key" String
            "$SemanticAttributes.HTTP_FLAVOR.key" "HTTP/1.1"
            "$SemanticAttributes.HTTP_METHOD.key" "GET"
            "$SemanticAttributes.NET_PEER_IP.key" "127.0.0.1"
            "$SemanticAttributes.NET_PEER_PORT.key" Long
          }
        }
        it.span(3) {
          name "/api/{module}/unit/{unitId}"
          kind INTERNAL
          attributes {
            "$SemanticAttributes.HTTP_METHOD.key" "GET"
            "$SemanticAttributes.HTTP_URL.key" "http://localhost:$port/api/firstModule/unit/unitOne"
            "camel.uri" String
          }
        }
        it.span(4) {
          name "moduleUnit"
          kind INTERNAL
          attributes {
            "camel.uri" "direct://moduleUnit"
          }
        }
      }
    }
  }
}
