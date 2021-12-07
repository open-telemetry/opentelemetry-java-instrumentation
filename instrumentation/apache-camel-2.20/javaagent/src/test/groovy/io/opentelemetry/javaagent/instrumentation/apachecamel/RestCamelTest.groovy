/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.RetryOnAddressAlreadyInUseTrait
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.SERVER

class RestCamelTest extends AgentInstrumentationSpecification implements RetryOnAddressAlreadyInUseTrait {

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
    port = PortUtils.findOpenPort()
    def app = new SpringApplication(RestConfig)
    app.setDefaultProperties(["restServer.port": port])
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
        template.sendBodyAndHeaders("direct:start", null, ["module": "firstModule", "unitId": "unitOne"])
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
            "apache-camel.uri" "direct://start"
          }
        }
        it.span(1) {
          name "GET"
          kind CLIENT
          parentSpanId(span(0).spanId)
          attributes {
            "$SemanticAttributes.HTTP_METHOD" "GET"
            "$SemanticAttributes.HTTP_STATUS_CODE" 200
            "apache-camel.uri" "rest://get:api/%7Bmodule%7D/unit/%7BunitId%7D"
          }
        }
        it.span(2) {
          name "/api/{module}/unit/{unitId}"
          kind SERVER
          parentSpanId(span(1).spanId)
          attributes {
            "$SemanticAttributes.HTTP_SCHEME" "http"
            "$SemanticAttributes.HTTP_HOST" "localhost:$port"
            "$SemanticAttributes.HTTP_TARGET" "/api/firstModule/unit/unitOne"
            "$SemanticAttributes.HTTP_STATUS_CODE" 200
            "$SemanticAttributes.HTTP_USER_AGENT" String
            "$SemanticAttributes.HTTP_FLAVOR" "1.1"
            "$SemanticAttributes.HTTP_METHOD" "GET"
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_PEER_PORT" Long
            "$SemanticAttributes.HTTP_SERVER_NAME" String
            "$SemanticAttributes.NET_TRANSPORT" IP_TCP
          }
        }
        it.span(3) {
          name "/api/{module}/unit/{unitId}"
          kind INTERNAL
          parentSpanId(span(2).spanId)
          attributes {
            "$SemanticAttributes.HTTP_METHOD" "GET"
            "$SemanticAttributes.HTTP_URL" "http://localhost:$port/api/firstModule/unit/unitOne"
            "apache-camel.uri" String
          }
        }
        it.span(4) {
          name "moduleUnit"
          kind INTERNAL
          parentSpanId(span(3).spanId)
          attributes {
            "apache-camel.uri" "direct://moduleUnit"
          }
        }
      }
    }
  }
}
