/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import spock.lang.Unroll

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

class HttpUrlConnectionTest extends HttpClientTest<HttpURLConnection> implements AgentTestTrait {

  static final RESPONSE = "Hello."
  static final STATUS = 200

  @Override
  HttpURLConnection buildRequest(String method, URI uri, Map<String, String> headers) {
    return uri.toURL().openConnection() as HttpURLConnection
  }

  @Override
  int sendRequest(HttpURLConnection connection, String method, URI uri, Map<String, String> headers) {
    if (uri.toString().contains("/read-timeout")) {
      connection.readTimeout = READ_TIMEOUT_MS
    }
    try {
      connection.setRequestMethod(method)
      headers.each { connection.setRequestProperty(it.key, it.value) }
      connection.setRequestProperty("Connection", "close")
      connection.useCaches = true
      connection.connectTimeout = CONNECT_TIMEOUT_MS
      def parentSpan = Span.current()
      def stream = connection.inputStream
      assert Span.current() == parentSpan
      stream.readLines()
      stream.close()
      return connection.getResponseCode()
    } finally {
      connection.disconnect()
    }
  }

  @Override
  int maxRedirects() {
    20
  }

  @Override
  Integer responseCodeOnRedirectError() {
    return 302
  }

  @Override
  boolean testReusedRequest() {
    // HttpURLConnection can't be reused
    return false
  }

  @Override
  boolean testCallback() {
    return false
  }

  @Override
  boolean testReadTimeout() {
    true
  }

  @Unroll
  def "trace request (useCaches: #useCaches)"() {
    setup:
    def url = resolveAddress("/success").toURL()
    runWithSpan("someTrace") {
      HttpURLConnection connection = url.openConnection()
      connection.useCaches = useCaches
      assert Span.current().getSpanContext().isValid()
      def stream = connection.inputStream
      def lines = stream.readLines()
      stream.close()
      assert connection.getResponseCode() == STATUS
      assert lines == [RESPONSE]

      // call again to ensure the cycling is ok
      connection = url.openConnection()
      connection.useCaches = useCaches
      assert Span.current().getSpanContext().isValid()
      // call before input stream to test alternate behavior
      assert connection.getResponseCode() == STATUS
      connection.inputStream
      stream = connection.inputStream // one more to ensure state is working
      lines = stream.readLines()
      stream.close()
      assert lines == [RESPONSE]
    }

    expect:
    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          name "someTrace"
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "HTTP GET"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.NET_TRANSPORT" IP_TCP
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" server.httpPort()
            "$SemanticAttributes.HTTP_URL" "$url"
            "$SemanticAttributes.HTTP_METHOD" "GET"
            "$SemanticAttributes.HTTP_STATUS_CODE" STATUS
            "$SemanticAttributes.HTTP_FLAVOR" "1.1"
          }
        }
        span(2) {
          name "test-http-server"
          kind SERVER
          childOf span(1)
          attributes {
          }
        }
        span(3) {
          name "HTTP GET"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.NET_TRANSPORT" IP_TCP
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" server.httpPort()
            "$SemanticAttributes.HTTP_URL" "$url"
            "$SemanticAttributes.HTTP_METHOD" "GET"
            "$SemanticAttributes.HTTP_STATUS_CODE" STATUS
            "$SemanticAttributes.HTTP_FLAVOR" "1.1"
          }
        }
        span(4) {
          name "test-http-server"
          kind SERVER
          childOf span(3)
          attributes {
          }
        }
      }
    }

    where:
    useCaches << [false, true]
  }

  def "test broken API usage"() {
    setup:
    def url = resolveAddress("/success").toURL()
    HttpURLConnection connection = runWithSpan("someTrace") {
      HttpURLConnection con = url.openConnection()
      con.setRequestProperty("Connection", "close")
      assert Span.current().getSpanContext().isValid()
      assert con.getResponseCode() == STATUS
      return con
    }

    expect:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "someTrace"
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "HTTP GET"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" server.httpPort()
            "$SemanticAttributes.NET_TRANSPORT" IP_TCP
            "$SemanticAttributes.HTTP_URL" "$url"
            "$SemanticAttributes.HTTP_METHOD" "GET"
            "$SemanticAttributes.HTTP_STATUS_CODE" STATUS
            "$SemanticAttributes.HTTP_FLAVOR" "1.1"
          }
        }
        serverSpan(it, 2, span(1))
      }
    }

    cleanup:
    connection.disconnect()

    where:
    iteration << (1..10)
  }

  def "test post request"() {
    setup:
    def url = resolveAddress("/success").toURL()
    runWithSpan("someTrace") {
      HttpURLConnection connection = url.openConnection()
      connection.setRequestMethod("POST")

      String urlParameters = "q=ASDF&w=&e=&r=12345&t="

      // Send post request
      connection.setDoOutput(true)
      DataOutputStream wr = new DataOutputStream(connection.getOutputStream())
      wr.writeBytes(urlParameters)
      wr.flush()
      wr.close()

      assert connection.getResponseCode() == STATUS

      def stream = connection.inputStream
      def lines = stream.readLines()
      stream.close()
      assert lines == [RESPONSE]
    }

    expect:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "someTrace"
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "HTTP POST"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.NET_TRANSPORT" IP_TCP
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" server.httpPort()
            "$SemanticAttributes.HTTP_URL" "$url"
            "$SemanticAttributes.HTTP_METHOD" "POST"
            "$SemanticAttributes.HTTP_STATUS_CODE" STATUS
            "$SemanticAttributes.HTTP_FLAVOR" "1.1"
          }
        }
        span(2) {
          name "test-http-server"
          kind SERVER
          childOf span(1)
          attributes {
          }
        }
      }
    }
  }
}
