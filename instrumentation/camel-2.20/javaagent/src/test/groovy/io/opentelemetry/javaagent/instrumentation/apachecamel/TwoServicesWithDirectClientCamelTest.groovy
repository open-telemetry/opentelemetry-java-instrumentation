/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.RetryOnAddressAlreadyInUseTrait
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.impl.DefaultCamelContext
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.SERVER

class TwoServicesWithDirectClientCamelTest extends AgentInstrumentationSpecification implements RetryOnAddressAlreadyInUseTrait {

  @Shared
  int portOne
  @Shared
  int portTwo
  @Shared
  ConfigurableApplicationContext server
  @Shared
  CamelContext clientContext

  def setupSpec() {
    withRetryOnAddressAlreadyInUse({
      setupSpecUnderRetry()
    })
  }

  def setupSpecUnderRetry() {
    portOne = PortUtils.findOpenPort()
    portTwo = PortUtils.findOpenPort()
    def app = new SpringApplication(TwoServicesConfig)
    app.setDefaultProperties(["service.one.port": portOne, "service.two.port": portTwo])
    server = app.run()
  }

  def createAndStartClient() {
    clientContext = new DefaultCamelContext()
    clientContext.addRoutes(new RouteBuilder() {
      void configure() {
        from("direct:input")
            .log("SENT Client request")
            .to("http://localhost:$portOne/serviceOne")
            .log("RECEIVED Client response")
      }
    })
    clientContext.start()
  }

  def cleanupSpec() {
    if (server != null) {
      server.close()
      server = null
    }
  }

  def "two camel service spans"() {
    setup:
    createAndStartClient()
    ProducerTemplate template = clientContext.createProducerTemplate()

    when:
    template.sendBody("direct:input", "Example request")

    then:
    assertTraces(1) {
      trace(0, 6) {
        it.span(0) {
          name "input"
          kind INTERNAL
          attributes {
            "camel.uri" "direct://input"
          }
        }
        it.span(1) {
          name "POST"
          kind CLIENT
          parentSpanId(span(0).spanId)
          attributes {
            "$SemanticAttributes.HTTP_METHOD" "POST"
            "$SemanticAttributes.HTTP_URL" "http://localhost:$portOne/serviceOne"
            "$SemanticAttributes.HTTP_STATUS_CODE" 200
            "camel.uri" "http://localhost:$portOne/serviceOne"
          }
        }
        it.span(2) {
          name "POST /serviceOne"
          kind SERVER
          parentSpanId(span(1).spanId)
          attributes {
            "$SemanticAttributes.HTTP_METHOD" "POST"
            "$SemanticAttributes.HTTP_URL" "http://localhost:$portOne/serviceOne"
            "$SemanticAttributes.HTTP_STATUS_CODE" 200
            "camel.uri" "http://0.0.0.0:$portOne/serviceOne"
          }
        }
        it.span(3) {
          name "POST"
          kind CLIENT
          parentSpanId(span(2).spanId)
          attributes {
            "$SemanticAttributes.HTTP_METHOD" "POST"
            "$SemanticAttributes.HTTP_URL" "http://127.0.0.1:$portTwo/serviceTwo"
            "$SemanticAttributes.HTTP_STATUS_CODE" 200
            "camel.uri" "http://127.0.0.1:$portTwo/serviceTwo"
          }
        }
        it.span(4) {
          name "POST /serviceTwo"
          kind SERVER
          parentSpanId(span(3).spanId)
          attributes {
            "$SemanticAttributes.HTTP_METHOD" "POST"
            "$SemanticAttributes.HTTP_STATUS_CODE" 200
            "$SemanticAttributes.HTTP_SCHEME" "http"
            "$SemanticAttributes.HTTP_TARGET" "/serviceTwo"
            "$SemanticAttributes.USER_AGENT_ORIGINAL" "Jakarta Commons-HttpClient/3.1"
            "$SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH" Long
            "$SemanticAttributes.HTTP_ROUTE" "/serviceTwo"
            "net.protocol.name" "http"
            "net.protocol.version" "1.1"
            "$SemanticAttributes.NET_HOST_NAME" "127.0.0.1"
            "$SemanticAttributes.NET_HOST_PORT" portTwo
            "$SemanticAttributes.NET_SOCK_PEER_ADDR" "127.0.0.1"
            "$SemanticAttributes.NET_SOCK_PEER_PORT" Long
          }
        }
        it.span(5) {
          name "POST /serviceTwo"
          kind INTERNAL
          parentSpanId(span(4).spanId)
          attributes {
            "$SemanticAttributes.HTTP_METHOD" "POST"
            "$SemanticAttributes.HTTP_URL" "http://127.0.0.1:$portTwo/serviceTwo"
            "camel.uri" "jetty:http://0.0.0.0:$portTwo/serviceTwo?arg=value"
          }
        }
      }
    }
  }
}
