/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test

import static io.opentelemetry.trace.Span.Kind.SERVER

import com.google.common.collect.ImmutableMap
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.OkHttpUtils
import io.opentelemetry.auto.test.utils.PortUtils
import io.opentelemetry.trace.attributes.SemanticAttributes
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Shared

class SingleServiceCamelSpringBootBasedTest extends AgentTestRunner {

  @Shared
  ConfigurableApplicationContext server
  @Shared
  OkHttpClient client = OkHttpUtils.client()
  @Shared
  int port = PortUtils.randomOpenPort()
  @Shared
  URI address = new URI("http://localhost:$port/")


  def setupSpec() {
    def app = new SpringApplication(SingleServiceConfig)
    app.setDefaultProperties(ImmutableMap.of("camelService.port", port))
    server = app.run()
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
          name "POST"
          attributes {
            "$SemanticAttributes.HTTP_METHOD.key" "POST"
            "$SemanticAttributes.HTTP_URL.key" "${address.resolve("/camelService")}"
            "camel.uri" "${address.resolve("/camelService")}".replace("localhost", "0.0.0.0")
          }
        }
      }
    }
  }
}
