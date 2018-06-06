import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.opentracing.Scope
import io.opentracing.SpanContext
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMap
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer
import ratpack.handling.Context
import spock.lang.Shared

import static datadog.trace.agent.test.ListWriterAssert.assertTraces
import static datadog.trace.agent.test.TestUtils.runUnderTrace
import static ratpack.groovy.test.embed.GroovyEmbeddedApp.ratpack
import static ratpack.http.HttpMethod.HEAD
import static ratpack.http.HttpMethod.POST

class HttpUrlConnectionTest extends AgentTestRunner {
  static {
    System.setProperty("dd.integration.httpurlconnection.enabled", "true")
    System.setProperty("http.keepAlive", "false")
  }

  @Shared
  def server = ratpack {
    handlers {
      all {
        String msg = "<html><body><h1>Hello test.</h1>\n"
        boolean isDDServer = true
        if (context.request.getHeaders().contains("is-dd-server")) {
          isDDServer = Boolean.parseBoolean(context.request.getHeaders().get("is-dd-server"))
        }
        if (isDDServer) {
          final SpanContext extractedContext =
            GlobalTracer.get()
              .extract(Format.Builtin.HTTP_HEADERS, new RatpackResponseAdapter(context))
          Scope scope =
            GlobalTracer.get()
              .buildSpan("test-http-server")
              .asChildOf((SpanContext) extractedContext)
              .startActive(true)
          scope.close()
        }

        request.body.then {
          response.status(200).send(msg)
        }
      }
    }
  }

  def "trace request with propagation"() {
    setup:
    runUnderTrace("someTrace") {
      HttpURLConnection connection = server.address.toURL().openConnection()
      assert GlobalTracer.get().scopeManager().active() != null
      def stream = connection.inputStream
      def lines = stream.readLines()
      stream.close()
      assert connection.getResponseCode() == 200
      assert lines == ["<html><body><h1>Hello test.</h1>"]

      // call again to ensure the cycling is ok
      connection = server.getAddress().toURL().openConnection()
      assert GlobalTracer.get().scopeManager().active() != null
      assert connection.getResponseCode() == 200 // call before input stream to test alternate behavior
      stream = connection.inputStream
      lines = stream.readLines()
      stream.close()
      assert lines == ["<html><body><h1>Hello test.</h1>"]
    }

    expect:
    assertTraces(TEST_WRITER, 3) {
      trace(0, 1) {
        span(0) {
          operationName "test-http-server"
          childOf(TEST_WRITER[2][4])
          errored false
          tags {
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName "test-http-server"
          childOf(TEST_WRITER[2][2])
          errored false
          tags {
            defaultTags()
          }
        }
      }
      trace(2, 5) {
        span(0) {
          operationName "someTrace"
          parent()
          errored false
          tags {
            defaultTags()
          }
        }
        span(1) {
          operationName "http.request.input_stream"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
        span(2) {
          operationName "http.request.response_code"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
        span(3) {
          operationName "http.request.response_code"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
        span(4) {
          operationName "http.request.input_stream"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
      }
    }
  }

  def "trace request without propagation"() {
    setup:
    runUnderTrace("someTrace") {
      HttpURLConnection connection = server.address.toURL().openConnection()
      connection.addRequestProperty("is-dd-server", "false")
      assert GlobalTracer.get().scopeManager().active() != null
      def stream = connection.inputStream
      def lines = stream.readLines()
      stream.close()
      assert connection.getResponseCode() == 200
      assert lines == ["<html><body><h1>Hello test.</h1>"]

      // call again to ensure the cycling is ok
      connection = server.getAddress().toURL().openConnection()
      connection.addRequestProperty("is-dd-server", "false")
      assert GlobalTracer.get().scopeManager().active() != null
      assert connection.getResponseCode() == 200 // call before input stream to test alternate behavior
      stream = connection.inputStream
      lines = stream.readLines()
      stream.close()
      assert lines == ["<html><body><h1>Hello test.</h1>"]
    }

    expect:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 5) {
        span(0) {
          operationName "someTrace"
          parent()
          errored false
          tags {
            defaultTags()
          }
        }
        span(1) {
          operationName "http.request.input_stream"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
        span(2) {
          operationName "http.request.response_code"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
        span(3) {
          operationName "http.request.response_code"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
        span(4) {
          operationName "http.request.input_stream"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
      }
    }
  }

  def "test response code"() {
    setup:
    runUnderTrace("someTrace") {
      HttpURLConnection connection = server.address.toURL().openConnection()
      connection.setRequestMethod(HEAD.name)
      connection.addRequestProperty("is-dd-server", "false")
      assert GlobalTracer.get().scopeManager().active() != null
      assert connection.getResponseCode() == 200
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
          operationName "http.request.response_code"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
            "$Tags.HTTP_METHOD.key" "HEAD"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
      }
    }
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

      assert connection.getResponseCode() == 200

      def stream = connection.inputStream
      def lines = stream.readLines()
      stream.close()
      assert lines == ["<html><body><h1>Hello test.</h1>"]
    }

    expect:
    assertTraces(TEST_WRITER, 2) {
      trace(0, 1) {
        span(0) {
          operationName "test-http-server"
          childOf(TEST_WRITER[1][3])
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
          operationName "http.request.input_stream"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
            "$Tags.HTTP_METHOD.key" "POST"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
        span(2) {
          operationName "http.request.response_code"
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "HttpURLConnection"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_URL.key" "$server.address"
            "$Tags.HTTP_METHOD.key" "POST"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
        span(3) {
          operationName "http.request.output_stream"
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
      assert connection.getResponseCode() == 200
      assert lines == ["<html><body><h1>Hello test.</h1>"]
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
