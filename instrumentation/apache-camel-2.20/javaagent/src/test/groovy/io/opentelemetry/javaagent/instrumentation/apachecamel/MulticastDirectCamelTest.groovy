/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.INTERNAL

class MulticastDirectCamelTest extends AgentInstrumentationSpecification {

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
            "apache-camel.uri" "direct://input"
          }
        }
        // there is no strict ordering of "first" and "second" span
        def indexOfFirst = span(1).name == "first" ? 1 : 2
        def indexOfSecond = span(1).name == "second" ? 1 : 2
        it.span(indexOfFirst) {
          name "first"
          kind INTERNAL
          parentSpanId parent.span(0).spanId
          attributes {
            "apache-camel.uri" "direct://first"
          }
        }
        it.span(indexOfSecond) {
          name "second"
          kind INTERNAL
          parentSpanId parent.span(0).spanId
          attributes {
            "apache-camel.uri" "direct://second"
          }
        }
      }
    }
  }
}
