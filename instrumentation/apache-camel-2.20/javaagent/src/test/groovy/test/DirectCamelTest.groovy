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

class DirectCamelTest extends AgentTestRunner {

  @Shared
  ConfigurableApplicationContext server

  def setupSpec() {
    def app = new SpringApplication(DirectConfig)
    server = app.run()
  }

  def cleanupSpec() {
    if (server != null) {
      server.close()
      server = null
    }
  }

  def "simple direct to a single services"() {
    setup:
    def camelContext = server.getBean(CamelContext)
    ProducerTemplate template = camelContext.createProducerTemplate()

    when:
    template.sendBody("direct:input", "Example request")

    then:
    assertTraces(1) {
      trace(0, 2) {
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
          name "receiver"
          kind INTERNAL
          parentSpanId parent.span(0).spanId
          attributes {
            "camel.uri" "direct://receiver"
          }
        }
      }
    }
  }
}
