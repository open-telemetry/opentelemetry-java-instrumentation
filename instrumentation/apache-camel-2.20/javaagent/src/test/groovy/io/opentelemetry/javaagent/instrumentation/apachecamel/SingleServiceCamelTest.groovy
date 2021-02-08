/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel

import static io.opentelemetry.api.trace.SpanKind.SERVER

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.RetryOnAddressAlreadyInUseTrait
import io.opentelemetry.instrumentation.test.utils.OkHttpUtils
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Shared

class SingleServiceCamelTest extends AgentInstrumentationSpecification implements RetryOnAddressAlreadyInUseTrait {

  @Shared
  ConfigurableApplicationContext server
  @Shared
  OkHttpClient client = OkHttpUtils.client()
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
    port = PortUtils.randomOpenPort()
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
    def url = HttpUrl.get(requestUrl)
    def request = new Request.Builder()
      .url(url)
      .method("POST",
        new FormBody.Builder().add("", "testContent").build())
      .build()

    when:
    client.newCall(request).execute()

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
