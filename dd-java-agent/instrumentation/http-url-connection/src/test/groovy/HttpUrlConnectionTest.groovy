import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.RatpackUtils
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.opentracing.propagation.TextMap
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer
import org.springframework.web.client.RestTemplate
import ratpack.handling.Context
import spock.lang.Shared

import static datadog.trace.agent.test.TestUtils.runUnderTrace
import static datadog.trace.agent.test.asserts.ListWriterAssert.assertTraces
import static ratpack.groovy.test.embed.GroovyEmbeddedApp.ratpack
import static ratpack.http.HttpMethod.HEAD
import static ratpack.http.HttpMethod.POST

class HttpUrlConnectionTest extends AgentTestRunner {
  static {
    System.setProperty("dd.integration.httpurlconnection.enabled", "true")
  }

  static final RESPONSE = "<html><body><h1>Hello test.</h1>"
  static final STATUS = 202

  @Shared
  def server = ratpack {
    handlers {
      all {
        RatpackUtils.handleDistributedRequest(context)

        request.body.then {
          if (it != null) {
            println "RECEIVED: $it.text"
          }
          response.status(STATUS)
          // Ratpack seems to be sending body with HEAD requests - RFC specifically forbids this.
          // This becomes a major problem with keep-alived requests - client seems to fail to parse
          // such response properly messing up following requests.
          if (request.method.isHead()) {
            response.send()
          } else {
            response.send(RESPONSE)
          }
        }
      }
    }
  }

  def "trace request with propagation"() {
    setup:
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

    expect:
    assertTraces(TEST_WRITER, 3) {
      trace(0, 1) {
        span(0) {
          operationName "test-http-server"
          childOf(TEST_WRITER[2][2])
          errored false
          tags {
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName "test-http-server"
          childOf(TEST_WRITER[2][1])
          errored false
          tags {
            defaultTags()
          }
        }
      }
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
          operationName "http.request.input-stream"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
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
          operationName "http.request.input-stream"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
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
  }

  def "trace request without propagation"() {
    setup:
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

    expect:
    assertTraces(TEST_WRITER, 1) {
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
          operationName "http.request.input-stream"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
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
          operationName "http.request.input-stream"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
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
  }

  def "test response code"() {
    setup:
    runUnderTrace("someTrace") {
      HttpURLConnection connection = server.address.toURL().openConnection()
      connection.setRequestMethod(HEAD.name)
      connection.addRequestProperty("is-dd-server", "false")
      assert GlobalTracer.get().scopeManager().active() != null
      assert connection.getResponseCode() == STATUS
    }

    expect:
    assertTraces(TEST_WRITER, 1) {
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
          operationName "http.request.input-stream"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
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
  }

  def "test broken API usage"() {
    setup:
    HttpURLConnection conn = runUnderTrace("someTrace") {
      HttpURLConnection connection = server.address.toURL().openConnection()
      connection.setRequestProperty("Connection", "close")
      connection.addRequestProperty("is-dd-server", "false")
      assert GlobalTracer.get().scopeManager().active() != null
      assert connection.getResponseCode() == STATUS
      return connection
    }

    expect:
    assertTraces(TEST_WRITER, 1) {
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
          operationName "http.request.input-stream"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
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
  }

  def "test post request"() {
    setup:
    runUnderTrace("someTrace") {
      HttpURLConnection connection = server.address.toURL().openConnection()
      connection.setRequestMethod(POST.name)

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
    assertTraces(TEST_WRITER, 2) {
      trace(0, 1) {
        span(0) {
          operationName "test-http-server"
          childOf(TEST_WRITER[1][1])
          errored false
          tags {
            defaultTags()
          }
        }
      }
      trace(1, 3) {
        span(0) {
          operationName "someTrace"
          parent()
          errored false
          tags {
            defaultTags()
          }
        }
        span(1) {
          operationName "http.request.input-stream"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
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
        span(2) {
          operationName "http.request.output-stream"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
            "$Tags.HTTP_METHOD.key" "POST"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
      }
    }
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
    assertTraces(TEST_WRITER, 1) {
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
    HttpURLConnection connection = server.address.toURL().openConnection()
    connection.addRequestProperty("is-dd-server", "false")
    def stream = connection.inputStream
    def lines = stream.readLines()
    stream.close()
    assert connection.getResponseCode() == STATUS
    assert lines == [RESPONSE]

    expect:
    assertTraces(TEST_WRITER, 0) {}
  }

  def "rest template"() {
    setup:
    runUnderTrace("someTrace") {
      RestTemplate restTemplate = new RestTemplate()
      String res = restTemplate.postForObject(server.address.toString(), "Hello", String)
      assert res == RESPONSE
    }

    expect:
    assertTraces(TEST_WRITER, 2) {
      trace(0, 1) {
        span(0) {
          operationName "test-http-server"
          childOf(TEST_WRITER[1][2])
          errored false
          tags {
            defaultTags()
          }
        }
      }
      trace(1, 4) {
        span(0) {
          operationName "someTrace"
          parent()
          errored false
          tags {
            defaultTags()
          }
        }
        span(1) {
          operationName "http.request.input-stream"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
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
        span(2) {
          operationName "http.request.output-stream"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
            "$Tags.HTTP_METHOD.key" "POST"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
        span(3) {
          operationName "http.request.connect"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
            "$Tags.HTTP_METHOD.key" "POST"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
      }
    }
  }

  private static class RatpackResponseAdapter implements TextMap {
    final Context context

    RatpackResponseAdapter(Context context) {
      this.context = context
    }

    @Override
    void put(String key, String value) {
      context.response.set(key, value)
    }

    @Override
    Iterator<Map.Entry<String, String>> iterator() {
      return context.request.getHeaders().asMultiValueMap().entrySet().iterator()
    }
  }
}
