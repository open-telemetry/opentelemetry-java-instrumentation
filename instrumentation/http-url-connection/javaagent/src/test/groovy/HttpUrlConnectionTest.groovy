/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import spock.lang.Requires
import spock.lang.Timeout
import spock.lang.Unroll
import sun.net.www.protocol.https.HttpsURLConnectionImpl

@Timeout(5)
class HttpUrlConnectionTest extends HttpClientTest implements AgentTestTrait {

  static final RESPONSE = "Hello."
  static final STATUS = 200

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    HttpURLConnection connection = uri.toURL().openConnection()
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
      callback?.call()
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
  Integer statusOnRedirectError() {
    return 302
  }

  @Unroll
  def "trace request with propagation (useCaches: #useCaches)"() {
    setup:
    def url = server.address.resolve("/success").toURL()
    runUnderTrace("someTrace") {
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
          errored false
          attributes {
          }
        }
        span(1) {
          name expectedOperationName("GET")
          kind CLIENT
          childOf span(0)
          errored false
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_NAME.key}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key}" server.address.port
            "${SemanticAttributes.HTTP_URL.key}" "$url"
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" STATUS
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
          }
        }
        span(2) {
          name "test-http-server"
          kind SERVER
          childOf span(1)
          errored false
          attributes {
          }
        }
        span(3) {
          name expectedOperationName("GET")
          kind CLIENT
          childOf span(0)
          errored false
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_NAME.key}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key}" server.address.port
            "${SemanticAttributes.HTTP_URL.key}" "$url"
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" STATUS
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
          }
        }
        span(4) {
          name "test-http-server"
          kind SERVER
          childOf span(3)
          errored false
          attributes {
          }
        }
      }
    }

    where:
    useCaches << [false, true]
  }

  def "trace request without propagation (useCaches: #useCaches)"() {
    setup:
    def url = server.address.resolve("/success").toURL()
    runUnderTrace("someTrace") {
      HttpURLConnection connection = url.openConnection()
      connection.useCaches = useCaches
      connection.addRequestProperty("is-test-server", "false")
      assert Span.current().getSpanContext().isValid()
      def stream = connection.inputStream
      connection.inputStream // one more to ensure state is working
      def lines = stream.readLines()
      stream.close()
      assert connection.getResponseCode() == STATUS
      assert lines == [RESPONSE]

      // call again to ensure the cycling is ok
      connection = url.openConnection()
      connection.useCaches = useCaches
      connection.addRequestProperty("is-test-server", "false")
      assert Span.current().getSpanContext().isValid()
      // call before input stream to test alternate behavior
      assert connection.getResponseCode() == STATUS
      stream = connection.inputStream
      lines = stream.readLines()
      stream.close()
      assert lines == [RESPONSE]
    }

    expect:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "someTrace"
          hasNoParent()
          errored false
          attributes {
          }
        }
        span(1) {
          name expectedOperationName("GET")
          kind CLIENT
          childOf span(0)
          errored false
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_NAME.key}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key}" server.address.port
            "${SemanticAttributes.HTTP_URL.key}" "$url"
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" STATUS
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
          }
        }
        span(2) {
          name expectedOperationName("GET")
          kind CLIENT
          childOf span(0)
          errored false
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_NAME.key}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key}" server.address.port
            "${SemanticAttributes.HTTP_URL.key}" "$url"
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" STATUS
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
          }
        }
      }
    }

    where:
    useCaches << [false, true]
  }

  def "test broken API usage"() {
    setup:
    def url = server.address.resolve("/success").toURL()
    HttpURLConnection connection = runUnderTrace("someTrace") {
      HttpURLConnection connection = url.openConnection()
      connection.setRequestProperty("Connection", "close")
      connection.addRequestProperty("is-test-server", "false")
      assert Span.current().getSpanContext().isValid()
      assert connection.getResponseCode() == STATUS
      return connection
    }

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "someTrace"
          hasNoParent()
          errored false
          attributes {
          }
        }
        span(1) {
          name expectedOperationName("GET")
          kind CLIENT
          childOf span(0)
          errored false
          attributes {
            "${SemanticAttributes.NET_PEER_NAME.key}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key}" server.address.port
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.HTTP_URL.key}" "$url"
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" STATUS
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
          }
        }
      }
    }

    cleanup:
    connection.disconnect()

    where:
    iteration << (1..10)
  }

  def "test post request"() {
    setup:
    def url = server.address.resolve("/success").toURL()
    runUnderTrace("someTrace") {
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
          errored false
          attributes {
          }
        }
        span(1) {
          name expectedOperationName("POST")
          kind CLIENT
          childOf span(0)
          errored false
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_NAME.key}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key}" server.address.port
            "${SemanticAttributes.HTTP_URL.key}" "$url"
            "${SemanticAttributes.HTTP_METHOD.key}" "POST"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" STATUS
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
          }
        }
        span(2) {
          name "test-http-server"
          kind SERVER
          childOf span(1)
          errored false
          attributes {
          }
        }
      }
    }
  }

  def "error span"() {
    def uri = server.address.resolve("/error")
    when:
    def url = uri.toURL()
    runUnderTrace("parent") {
      doRequest(method, uri)
    }

    then:
    def expectedException = new IOException("Server returned HTTP response code: 500 for URL: $url")
    thrown(IOException)
    assertTraces(1) {
      trace(0, 3 + extraClientSpans()) {
        basicSpan(it, 0, "parent", null, expectedException)
        clientSpan(it, 1, span(0), method, uri, 500, expectedException)
        serverSpan(it, 2 + extraClientSpans(), span(1 + extraClientSpans()))
      }
    }

    where:
    method = "GET"
  }

  // This test makes no sense on IBM JVM because there is no HttpsURLConnectionImpl class there
  @Requires({ !System.getProperty("java.vm.name").contains("IBM J9 VM") })
  def "Make sure we can load HttpsURLConnectionImpl"() {
    when:
    def instance = new HttpsURLConnectionImpl(null, null, null)

    then:
    instance != null
  }
}
