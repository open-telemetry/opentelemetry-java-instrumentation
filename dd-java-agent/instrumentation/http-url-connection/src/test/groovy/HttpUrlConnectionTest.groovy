import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.Config
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer
import org.springframework.web.client.RestTemplate
import spock.lang.AutoCleanup
import spock.lang.Shared

import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static datadog.trace.agent.test.utils.TraceUtils.withConfigOverride
import static datadog.trace.instrumentation.http_url_connection.HttpUrlConnectionInstrumentation.HttpUrlState.COMPONENT_NAME
import static datadog.trace.instrumentation.http_url_connection.HttpUrlConnectionInstrumentation.HttpUrlState.OPERATION_NAME

class HttpUrlConnectionTest extends AgentTestRunner {

  static final RESPONSE = "<html><body><h1>Hello test.</h1>"
  static final STATUS = 202

  @AutoCleanup
  @Shared
  def server = httpServer {
    handlers {
      all {
        handleDistributedRequest()

        response.status(STATUS).send(RESPONSE)
      }
    }
  }

  def "trace request with propagation (useCaches: #useCaches)"() {
    setup:
    withConfigOverride("dd.$Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN", "$renameService") {
      runUnderTrace("someTrace") {
        HttpURLConnection connection = server.address.toURL().openConnection()
        connection.useCaches = useCaches
        assert GlobalTracer.get().scopeManager().active() != null
        def stream = connection.inputStream
        def lines = stream.readLines()
        stream.close()
        assert connection.getResponseCode() == STATUS
        assert lines == [RESPONSE]

        // call again to ensure the cycling is ok
        connection = server.getAddress().toURL().openConnection()
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
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" COMPONENT_NAME
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
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
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" COMPONENT_NAME
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
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

  def "trace request without propagation (useCaches: #useCaches)"() {
    setup:
    withConfigOverride("dd.$Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN", "$renameService") {
      runUnderTrace("someTrace") {
        HttpURLConnection connection = server.address.toURL().openConnection()
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
        connection = server.getAddress().toURL().openConnection()
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
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" COMPONENT_NAME
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
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
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" COMPONENT_NAME
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
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

  def "test response code"() {
    setup:
    withConfigOverride("dd.$Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN", "$renameService") {
      runUnderTrace("someTrace") {
        HttpURLConnection connection = server.address.toURL().openConnection()
        connection.setRequestMethod("HEAD")
        connection.addRequestProperty("is-dd-server", "false")
        assert GlobalTracer.get().scopeManager().active() != null
        assert connection.getResponseCode() == STATUS
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
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" COMPONENT_NAME
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
            "$Tags.HTTP_METHOD.key" "HEAD"
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

  def "test broken API usage"() {
    setup:
    HttpURLConnection conn = withConfigOverride("dd.$Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN", "$renameService") {
      runUnderTrace("someTrace") {
        HttpURLConnection connection = server.address.toURL().openConnection()
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
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" COMPONENT_NAME
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
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

  def "test post request"() {
    setup:
    withConfigOverride("dd.$Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN", "$renameService") {
      runUnderTrace("someTrace") {
        HttpURLConnection connection = server.address.toURL().openConnection()
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
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" COMPONENT_NAME
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
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

  def "request that looks like a trace submission is ignored"() {
    setup:
    runUnderTrace("someTrace") {
      HttpURLConnection connection = server.address.toURL().openConnection()
      connection.addRequestProperty("Datadog-Meta-Lang", "false")
      connection.addRequestProperty("is-dd-server", "false")
      def stream = connection.inputStream
      def lines = stream.readLines()
      stream.close()
      assert connection.getResponseCode() == STATUS
      assert lines == [RESPONSE]
    }

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "someTrace"
          parent()
          errored false
          tags {
            defaultTags()
          }
        }
      }
    }
  }

  def "top level httpurlconnection tracing disabled"() {
    setup:
    withConfigOverride("dd.$Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN", "$renameService") {
      HttpURLConnection connection = server.address.toURL().openConnection()
      connection.addRequestProperty("is-dd-server", "false")
      def stream = connection.inputStream
      def lines = stream.readLines()
      stream.close()
      assert connection.getResponseCode() == STATUS
      assert lines == [RESPONSE]
    }

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName renameService ? "localhost" : "unnamed-java-app"
          operationName OPERATION_NAME
          parent()
          errored false
          tags {
            "$Tags.COMPONENT.key" COMPONENT_NAME
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
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
    renameService << [false, true]
  }

  def "rest template"() {
    setup:
    withConfigOverride("dd.$Config.HTTP_CLIENT_HOST_SPLIT_BY_DOMAIN", "$renameService") {
      runUnderTrace("someTrace") {
        RestTemplate restTemplate = new RestTemplate()
        String res = restTemplate.postForObject(server.address.toString(), "Hello", String)
        assert res == "$RESPONSE"
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
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" COMPONENT_NAME
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
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
}
