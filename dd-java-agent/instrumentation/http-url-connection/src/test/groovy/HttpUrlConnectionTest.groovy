import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.api.Config
import datadog.trace.api.DDSpanTypes
import datadog.trace.instrumentation.http_url_connection.HttpUrlConnectionDecorator
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer
import spock.lang.Ignore
import spock.lang.Requires
import sun.net.www.protocol.https.HttpsURLConnectionImpl

import static datadog.trace.agent.test.utils.ConfigUtils.withConfigOverride
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static datadog.trace.instrumentation.http_url_connection.HttpUrlConnectionInstrumentation.HttpUrlState.OPERATION_NAME

class HttpUrlConnectionTest extends HttpClientTest<HttpUrlConnectionDecorator> {

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
      def parentSpan = GlobalTracer.get().scopeManager().active()
      def stream = connection.inputStream
      assert GlobalTracer.get().scopeManager().active() == parentSpan
      stream.readLines()
      stream.close()
      callback?.call()
      return connection.getResponseCode()
    } finally {
      connection.disconnect()
    }
  }

  @Override
  HttpUrlConnectionDecorator decorator() {
    return HttpUrlConnectionDecorator.DECORATE
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Ignore
  def "trace request with propagation (useCaches: #useCaches)"() {
    setup:
    def url = server.address.resolve("/success").toURL()
    withConfigOverride(Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "$renameService") {
      runUnderTrace("someTrace") {
        HttpURLConnection connection = url.openConnection()
        connection.useCaches = useCaches
        assert GlobalTracer.get().scopeManager().active() != null
        def stream = connection.inputStream
        def lines = stream.readLines()
        stream.close()
        assert connection.getResponseCode() == STATUS
        assert lines == [RESPONSE]

        // call again to ensure the cycling is ok
        connection = url.openConnection()
        connection.useCaches = useCaches
        assert GlobalTracer.get().scopeManager().active() != null
        assert connection.getResponseCode() == STATUS // call before input stream to test alternate behavior
        connection.inputStream
        stream = connection.inputStream // one more to ensure state is working
        lines = stream.readLines()
        stream.close()
        assert lines == [RESPONSE]
      }
    }

    expect:
    assertTraces(3) {
      server.distributedRequestTrace(it, 0, TEST_WRITER[2][2])
      server.distributedRequestTrace(it, 1, TEST_WRITER[2][1])
      trace(2, 3) {
        span(0) {
          operationName "someTrace"
          parent()
          errored false
          tags {
            defaultTags()
          }
        }
        span(1) {
          serviceName renameService ? "localhost" : "unnamed-java-app"
          operationName OPERATION_NAME
          resourceName "GET $url.path"
          spanType DDSpanTypes.HTTP_CLIENT
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "http-url-connection"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.HTTP_URL.key" "$url"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" STATUS
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
        span(2) {
          serviceName renameService ? "localhost" : "unnamed-java-app"
          operationName OPERATION_NAME
          resourceName "GET $url.path"
          spanType DDSpanTypes.HTTP_CLIENT
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "http-url-connection"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.HTTP_URL.key" "$url"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" STATUS
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
      }
    }

    where:
    useCaches << [false, true]
    renameService << [true, false]
  }

  @Ignore
  def "trace request without propagation (useCaches: #useCaches)"() {
    setup:
    def url = server.address.resolve("/success").toURL()
    withConfigOverride(Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "$renameService") {
      runUnderTrace("someTrace") {
        HttpURLConnection connection = url.openConnection()
        connection.useCaches = useCaches
        connection.addRequestProperty("is-dd-server", "false")
        assert GlobalTracer.get().scopeManager().active() != null
        def stream = connection.inputStream
        connection.inputStream // one more to ensure state is working
        def lines = stream.readLines()
        stream.close()
        assert connection.getResponseCode() == STATUS
        assert lines == [RESPONSE]

        // call again to ensure the cycling is ok
        connection = url.openConnection()
        connection.useCaches = useCaches
        connection.addRequestProperty("is-dd-server", "false")
        assert GlobalTracer.get().scopeManager().active() != null
        assert connection.getResponseCode() == STATUS // call before input stream to test alternate behavior
        stream = connection.inputStream
        lines = stream.readLines()
        stream.close()
        assert lines == [RESPONSE]
      }
    }

    expect:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "someTrace"
          parent()
          errored false
          tags {
            defaultTags()
          }
        }
        span(1) {
          serviceName renameService ? "localhost" : "unnamed-java-app"
          operationName OPERATION_NAME
          resourceName "GET $url.path"
          spanType DDSpanTypes.HTTP_CLIENT
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "http-url-connection"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.HTTP_URL.key" "$url"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" STATUS
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
        span(2) {
          serviceName renameService ? "localhost" : "unnamed-java-app"
          operationName OPERATION_NAME
          resourceName "GET $url.path"
          spanType DDSpanTypes.HTTP_CLIENT
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "http-url-connection"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.HTTP_URL.key" "$url"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" STATUS
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
      }
    }

    where:
    useCaches << [false, true]
    renameService << [false, true]
  }

  @Ignore
  def "test broken API usage"() {
    setup:
    def url = server.address.resolve("/success").toURL()
    HttpURLConnection conn = withConfigOverride(Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "$renameService") {
      runUnderTrace("someTrace") {
        HttpURLConnection connection = url.openConnection()
        connection.setRequestProperty("Connection", "close")
        connection.addRequestProperty("is-dd-server", "false")
        assert GlobalTracer.get().scopeManager().active() != null
        assert connection.getResponseCode() == STATUS
        return connection
      }
    }

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "someTrace"
          parent()
          errored false
          tags {
            defaultTags()
          }
        }
        span(1) {
          serviceName renameService ? "localhost" : "unnamed-java-app"
          operationName OPERATION_NAME
          resourceName "GET $url.path"
          spanType DDSpanTypes.HTTP_CLIENT
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "http-url-connection"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.HTTP_URL.key" "$url"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" STATUS
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
      }
    }

    cleanup:
    conn.disconnect()

    where:
    iteration << (1..10)
    renameService = (iteration % 2 == 0) // alternate even/odd
  }

  @Ignore
  def "test post request"() {
    setup:
    def url = server.address.resolve("/success").toURL()
    withConfigOverride(Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN, "$renameService") {
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
    }

    expect:
    assertTraces(2) {
      server.distributedRequestTrace(it, 0, TEST_WRITER[1][1])
      trace(1, 2) {
        span(0) {
          operationName "someTrace"
          parent()
          errored false
          tags {
            defaultTags()
          }
        }
        span(1) {
          serviceName renameService ? "localhost" : "unnamed-java-app"
          operationName OPERATION_NAME
          resourceName "POST $url.path"
          spanType DDSpanTypes.HTTP_CLIENT
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "http-url-connection"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.HTTP_URL.key" "$url"
            "$Tags.HTTP_METHOD.key" "POST"
            "$Tags.HTTP_STATUS.key" STATUS
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
      }
    }

    where:
    renameService << [false, true]
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
