/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.instrumentation.httpurlconnection.HttpUrlConnectionDecorator
import io.opentelemetry.auto.test.base.HttpClientTest
import spock.lang.Ignore
import spock.lang.Requires
import sun.net.www.protocol.https.HttpsURLConnectionImpl

import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.trace.Span.Kind.CLIENT

class HttpUrlConnectionTest extends HttpClientTest {

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
      def parentSpan = TEST_TRACER.getCurrentSpan()
      def stream = connection.inputStream
      assert TEST_TRACER.getCurrentSpan() == parentSpan
      stream.readLines()
      stream.close()
      callback?.call()
      return connection.getResponseCode()
    } finally {
      connection.disconnect()
    }
  }

  @Override
  String component() {
    return HttpUrlConnectionDecorator.DECORATE.getComponentName()
  }

  @Override
  boolean testCircularRedirects() {
    false
  }

  @Ignore
  def "trace request with propagation (useCaches: #useCaches)"() {
    setup:
    def url = server.address.resolve("/success").toURL()
    runUnderTrace("someTrace") {
      HttpURLConnection connection = url.openConnection()
      connection.useCaches = useCaches
      assert activeSpan() != null
      def stream = connection.inputStream
      def lines = stream.readLines()
      stream.close()
      assert connection.getResponseCode() == STATUS
      assert lines == [RESPONSE]

      // call again to ensure the cycling is ok
      connection = url.openConnection()
      connection.useCaches = useCaches
      assert activeSpan() != null
      assert connection.getResponseCode() == STATUS // call before input stream to test alternate behavior
      connection.inputStream
      stream = connection.inputStream // one more to ensure state is working
      lines = stream.readLines()
      stream.close()
      assert lines == [RESPONSE]
    }

    expect:
    assertTraces(3) {
      server.distributedRequestTrace(it, 0, traces[2][2])
      server.distributedRequestTrace(it, 1, traces[2][1])
      trace(2, 3) {
        span(0) {
          operationName "someTrace"
          parent()
          errored false
          tags {
          }
        }
        span(1) {
          operationName expectedOperationName("GET")
          spanKind CLIENT
          childOf span(0)
          errored false
          tags {
            "$MoreTags.NET_PEER_NAME" "localhost"
            "$MoreTags.NET_PEER_PORT" server.address.port
            "$Tags.HTTP_URL" "$url"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" STATUS
          }
        }
        span(2) {
          operationName expectedOperationName("GET")
          spanKind CLIENT
          childOf span(0)
          errored false
          tags {
            "$MoreTags.NET_PEER_NAME" "localhost"
            "$MoreTags.NET_PEER_PORT" server.address.port
            "$Tags.HTTP_URL" "$url"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" STATUS
          }
        }
      }
    }

    where:
    useCaches << [false, true]
  }

  @Ignore
  def "trace request without propagation (useCaches: #useCaches)"() {
    setup:
    def url = server.address.resolve("/success").toURL()
    runUnderTrace("someTrace") {
      HttpURLConnection connection = url.openConnection()
      connection.useCaches = useCaches
      connection.addRequestProperty("is-test-server", "false")
      assert activeSpan() != null
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
      assert activeSpan() != null
      assert connection.getResponseCode() == STATUS // call before input stream to test alternate behavior
      stream = connection.inputStream
      lines = stream.readLines()
      stream.close()
      assert lines == [RESPONSE]
    }

    expect:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "someTrace"
          parent()
          errored false
          tags {
          }
        }
        span(1) {
          operationName expectedOperationName("GET")
          spanKind CLIENT
          childOf span(0)
          errored false
          tags {
            "$MoreTags.NET_PEER_NAME" "localhost"
            "$MoreTags.NET_PEER_PORT" server.address.port
            "$Tags.HTTP_URL" "$url"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" STATUS
          }
        }
        span(2) {
          operationName expectedOperationName("GET")
          spanKind CLIENT
          childOf span(0)
          errored false
          tags {
            "$MoreTags.NET_PEER_NAME" "localhost"
            "$MoreTags.NET_PEER_PORT" server.address.port
            "$Tags.HTTP_URL" "$url"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" STATUS
          }
        }
      }
    }

    where:
    useCaches << [false, true]
  }

  @Ignore
  def "test broken API usage"() {
    setup:
    def url = server.address.resolve("/success").toURL()
    runUnderTrace("someTrace") {
      HttpURLConnection connection = url.openConnection()
      connection.setRequestProperty("Connection", "close")
      connection.addRequestProperty("is-test-server", "false")
      assert activeSpan() != null
      assert connection.getResponseCode() == STATUS
      return connection
    }

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "someTrace"
          parent()
          errored false
          tags {
          }
        }
        span(1) {
          operationName expectedOperationName("GET")
          spanKind CLIENT
          childOf span(0)
          errored false
          tags {
            "$MoreTags.NET_PEER_NAME" "localhost"
            "$MoreTags.NET_PEER_PORT" server.address.port
            "$Tags.HTTP_URL" "$url"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" STATUS
          }
        }
      }
    }

    cleanup:
    conn.disconnect()

    where:
    iteration << (1..10)
  }

  @Ignore
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
    assertTraces(2) {
      server.distributedRequestTrace(it, 0, traces[1][1])
      trace(1, 2) {
        span(0) {
          operationName "someTrace"
          parent()
          errored false
          tags {
          }
        }
        span(1) {
          operationName expectedOperationName("POST")
          spanKind CLIENT
          childOf span(0)
          errored false
          tags {
            "$MoreTags.NET_PEER_NAME" "localhost"
            "$MoreTags.NET_PEER_PORT" server.address.port
            "$Tags.HTTP_URL" "$url"
            "$Tags.HTTP_METHOD" "POST"
            "$Tags.HTTP_STATUS" STATUS
          }
        }
      }
    }
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
