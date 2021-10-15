/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.propagation.TextMapSetter
import io.opentelemetry.instrumentation.api.tracer.net.NetPeerAttributes
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import spock.lang.Shared

import static io.opentelemetry.instrumentation.api.tracer.HttpStatusConverter.statusFromHttpStatus
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

class HttpClientTracerTest extends BaseTracerTest {

  @Shared
  def testUrl = new URI("http://myhost:123/somepath")

  @Shared
  def testUrlMapped = new URI("http://dogs.com:123/somepath")

  @Shared
  def testUserAgent = "Apache HttpClient"

  def "test onRequest"() {
    setup:
    def tracer = newTracer()

    when:
    tracer.onRequest(span, req)

    then:
    if (req) {
      1 * span.setAttribute(SemanticAttributes.NET_TRANSPORT, IP_TCP)
      1 * span.setAttribute(SemanticAttributes.HTTP_METHOD, req.method)
      1 * span.setAttribute(SemanticAttributes.HTTP_URL, "$req.url")
      1 * span.setAttribute(SemanticAttributes.NET_PEER_NAME, req.url.host)
      1 * span.setAttribute(SemanticAttributes.NET_PEER_PORT, req.url.port)
      1 * span.setAttribute(SemanticAttributes.HTTP_USER_AGENT, req["User-Agent"])
      1 * span.setAttribute(SemanticAttributes.HTTP_FLAVOR, "1.1")
    }
    0 * _

    where:
    req << [
      null,
      [method: "test-method", url: testUrl, "User-Agent": testUserAgent]
    ]
  }

  def "test onRequest with mapped peer"() {
    setup:
    def tracer = newTracer()
    def req = [method: "test-method", url: testUrlMapped, "User-Agent": testUserAgent]

    when:
    tracer.onRequest(span, req)

    then:
    if (req) {
      1 * span.setAttribute(SemanticAttributes.NET_TRANSPORT, IP_TCP)
      1 * span.setAttribute(SemanticAttributes.HTTP_METHOD, req.method)
      1 * span.setAttribute(SemanticAttributes.HTTP_URL, "$req.url")
      1 * span.setAttribute(SemanticAttributes.NET_PEER_NAME, req.url.host)
      1 * span.setAttribute(SemanticAttributes.NET_PEER_PORT, req.url.port)
      1 * span.setAttribute(SemanticAttributes.PEER_SERVICE, "dogsservice")
      1 * span.setAttribute(SemanticAttributes.HTTP_USER_AGENT, req["User-Agent"])
      1 * span.setAttribute(SemanticAttributes.HTTP_FLAVOR, "1.1")
    }
    0 * _
  }

  def "test url handling for #url"() {
    setup:
    def tracer = newTracer()

    when:
    tracer.onRequest(span, req)

    then:
    1 * span.setAttribute(SemanticAttributes.NET_TRANSPORT, IP_TCP)
    if (expectedUrl != null) {
      1 * span.setAttribute(SemanticAttributes.HTTP_URL, expectedUrl)
    }
    1 * span.setAttribute(SemanticAttributes.HTTP_METHOD, null)
    1 * span.setAttribute(SemanticAttributes.HTTP_FLAVOR, "1.1")
    1 * span.setAttribute(SemanticAttributes.HTTP_USER_AGENT, null)
    if (hostname) {
      1 * span.setAttribute(SemanticAttributes.NET_PEER_NAME, hostname)
    }
    if (port) {
      1 * span.setAttribute(SemanticAttributes.NET_PEER_PORT, port)
    }
    0 * _

    where:
    tagQueryString | url                                  | expectedUrl                          | expectedQuery | expectedFragment | hostname | port
    false          | null                                 | null                                 | null          | null             | null     | null
    false          | ""                                   | ""                                   | ""            | null             | null     | null
    false          | "/path?query"                        | "/path?query"                        | ""            | null             | null     | null
    false          | "https://host:0"                     | "https://host:0"                     | ""            | null             | "host"   | null
    false          | "https://host/path"                  | "https://host/path"                  | ""            | null             | "host"   | null
    false          | "http://host:99/path?query#fragment" | "http://host:99/path?query#fragment" | ""            | null             | "host"   | 99
    false          | "https://usr:pswd@host/path"         | "https://host/path"                  | ""            | null             | "host"   | null

    req = [url: url == null ? null : new URI(url)]
  }

  def "test onResponse"() {
    setup:
    def tracer = newTracer()
    def statusCode = status != null ? statusFromHttpStatus(status, SpanKind.CLIENT) : null

    when:
    tracer.onResponse(span, resp)

    then:
    if (status) {
      1 * span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE, status)
    }
    if (statusCode != null && statusCode != StatusCode.UNSET) {
      1 * span.setStatus(statusCode)
    }
    0 * _

    where:
    status | resp
    200    | [status: 200]
    399    | [status: 399]
    400    | [status: 400]
    499    | [status: 499]
    500    | [status: 500]
    500    | [status: 500]
    600    | [status: 600]
    null   | [status: null]
    null   | null
  }

  @Override
  def newTracer() {
    def netPeerAttributes = new NetPeerAttributes([
      "1.2.3.4": "catservice", "dogs.com": "dogsservice"
    ])
    return new HttpClientTracer<Map, Map, Map>(netPeerAttributes) {

      @Override
      protected String method(Map m) {
        return m.method
      }

      @Override
      protected URI url(Map m) {
        return m.url
      }

      @Override
      protected Integer status(Map m) {
        return m.status
      }

      @Override
      protected String requestHeader(Map m, String name) {
        return m[name]
      }

      @Override
      protected String responseHeader(Map m, String name) {
        return m[name]
      }

      @Override
      protected TextMapSetter<Map> getSetter() {
        return null
      }

      @Override
      protected String getInstrumentationName() {
        return "HttpClientTracerTest"
      }
    }
  }
}
