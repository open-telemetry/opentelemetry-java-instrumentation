/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test

import static io.opentelemetry.api.trace.Span.Kind.INTERNAL

import io.opentelemetry.instrumentation.test.AgentTestRunner
import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Shared

class MulticastDirectCamelTest extends AgentTestRunner {

  @Shared
  ConfigurableApplicationContext server

  def setupSpec() {
    def app = new SpringApplication(MulticastConfig)
    server = app.run()
  }

  def cleanupSpec() {
    if (server != null) {
      server.close()
      server = null
    }
  }

  def "parallel multicast to two child services"() {
    setup:
    def camelContext = server.getBean(CamelContext)
    ProducerTemplate template = camelContext.createProducerTemplate()

    when:
    template.sendBody("direct:input", "Example request")

    then:
    assertTraces(1) {
      trace(0, 3) {
        def parent = it
        it.span(0) {
          name "input"
          kind INTERNAL
          hasNoParent()
          attributes {
            "camel.uri" "direct://input"
          }
        }
        it.span(1) {
          name "second"
          kind INTERNAL
          parentSpanId parent.span(0).spanId
          attributes {
            "camel.uri" "direct://second"
          }
        }
        it.span(2) {
          name "first"
          kind INTERNAL
          parentSpanId parent.span(0).spanId
          attributes {
            "camel.uri" "direct://first"
          }
        }
      }
    }
  }
}
