/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.StatusCode.ERROR
import static io.opentelemetry.instrumentation.test.utils.PortUtils.UNUSABLE_PORT
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

class UrlConnectionTest extends AgentInstrumentationSpecification {

  def "trace request with connection failure #scheme"() {
    when:
    runWithSpan("someTrace") {
      URLConnection connection = url.openConnection()
      connection.setConnectTimeout(10000)
      connection.setReadTimeout(10000)
      assert Span.current() != null
      connection.inputStream
    }

    then:
    thrown ConnectException

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "someTrace"
          hasNoParent()
          status ERROR
          errorEvent ConnectException, String
        }
        span(1) {
          name "HTTP GET"
          kind CLIENT
          childOf span(0)
          status ERROR
          errorEvent ConnectException, String
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_NAME.key}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key}" UNUSABLE_PORT
            "${SemanticAttributes.HTTP_URL.key}" "$url"
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
          }
        }
      }
    }

    where:
    scheme << ["http", "https"]

    url = new URI("$scheme://localhost:$UNUSABLE_PORT").toURL()
  }
}
