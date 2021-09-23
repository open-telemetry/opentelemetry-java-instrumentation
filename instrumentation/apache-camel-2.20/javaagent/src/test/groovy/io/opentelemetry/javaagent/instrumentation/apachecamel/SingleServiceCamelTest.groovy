/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.RetryOnAddressAlreadyInUseTrait
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.testing.internal.armeria.client.WebClient
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.SERVER

class SingleServiceCamelTest extends AgentInstrumentationSpecification implements RetryOnAddressAlreadyInUseTrait {

  @Shared
  ConfigurableApplicationContext server
  @Shared
  WebClient client = WebClient.of()
  @Shared
  int port
  @Shared
  URI address

  def setupSpec() {
    withRetryOnAddressAlreadyInUse({
      setupSpecUnderRetry()
    })
  }

  def setupSpecUnderRetry() {
    port = PortUtils.findOpenPort()
    address = new URI("http://localhost:$port/")
    def app = new SpringApplication(SingleServiceConfig)
    app.setDefaultProperties(["camelService.port": port])
    server = app.run()
    println getClass().name + " http server started at: http://localhost:$port/"
  }

  def cleanupSpec() {
    if (server != null) {
      server.close()
      server = null
    }
  }

  def "single camel service span"() {
    setup:
    def requestUrl = address.resolve("/camelService")

    when:
    client.post(requestUrl.toString(), "testContent").aggregate().join()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          kind SERVER
          name "/camelService"
          attributes {
            "$SemanticAttributes.HTTP_METHOD.key" "POST"
            "$SemanticAttributes.HTTP_URL.key" "${address.resolve("/camelService")}"
            "apache-camel.uri" "${address.resolve("/camelService")}".replace("localhost", "0.0.0.0")
          }
        }
      }
    }
  }
}
