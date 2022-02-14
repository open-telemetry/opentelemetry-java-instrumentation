/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.testing.internal.armeria.client.WebClient
import spark.Spark
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

class SparkJavaBasedTest extends AgentInstrumentationSpecification {

  @Shared
  int port

  @Shared
  WebClient client

  def setupSpec() {
    port = PortUtils.findOpenPort()
    TestSparkJavaApplication.initSpark(port)
    client = WebClient.of("http://localhost:${port}")
  }

  def cleanupSpec() {
    Spark.stop()
  }

  def "generates spans"() {
    when:
    def response = client.get("/param/asdf1234").aggregate().join()

    then:
    port != 0
    def content = response.contentUtf8()
    content == "Hello asdf1234"

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "/param/:param"
          kind SERVER
          hasNoParent()
          attributes {
            "$SemanticAttributes.NET_TRANSPORT" IP_TCP
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_PEER_PORT" Long
            "$SemanticAttributes.HTTP_SCHEME" "http"
            "$SemanticAttributes.HTTP_HOST" "localhost:$port"
            "$SemanticAttributes.HTTP_TARGET" "/param/asdf1234"
            "$SemanticAttributes.HTTP_METHOD" "GET"
            "$SemanticAttributes.HTTP_STATUS_CODE" 200
            "$SemanticAttributes.HTTP_FLAVOR" "1.1"
            "$SemanticAttributes.HTTP_USER_AGENT" String
          }
        }
      }
    }
  }

}
