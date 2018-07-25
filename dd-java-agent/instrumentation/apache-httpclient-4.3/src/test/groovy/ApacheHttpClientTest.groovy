import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.RatpackUtils
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.message.BasicHeader
import spock.lang.Shared

import static datadog.trace.agent.test.ListWriterAssert.assertTraces
import static datadog.trace.agent.test.TestUtils.runUnderTrace
import static ratpack.groovy.test.embed.GroovyEmbeddedApp.ratpack

class ApacheHttpClientTest extends AgentTestRunner {

  @Shared
  def server = ratpack {
    handlers {
      get {
        RatpackUtils.handleDistributedRequest(context)

        String msg = "<html><body><h1>Hello test.</h1>\n"
        response.status(200).send(msg)
      }
    }
  }
  @Shared
  int port = server.getAddress().port

  def "trace request with propagation"() {
    setup:
    final HttpClientBuilder builder = HttpClientBuilder.create()

    final HttpClient client = builder.build()
    runUnderTrace("someTrace") {
      try {
        HttpResponse response = client.execute(new HttpGet(server.getAddress()))
        assert response.getStatusLine().getStatusCode() == 200
      } catch (Exception e) {
        e.printStackTrace()
        throw new RuntimeException(e)
      }
    }

    expect:
    // one trace on the server, one trace on the client
    assertTraces(TEST_WRITER, 2) {
      trace(0, 1) {
        span(0) {
          childOf TEST_WRITER[1][2]
          serviceName "unnamed-java-app"
          operationName "test-http-server"
          resourceName "test-http-server"
          errored false
          tags {
            defaultTags()
          }
        }
      }
      trace(1, 3) {
        span(0) {
          parent()
          serviceName "unnamed-java-app"
          operationName "someTrace"
          resourceName "someTrace"
          errored false
          tags {
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName "unnamed-java-app"
          operationName "apache.http"
          resourceName "apache.http"
          errored false
          tags {
            defaultTags()
            "$Tags.COMPONENT.key" "apache-httpclient"
          }
        }
        span(2) {
          childOf span(1)
          serviceName "unnamed-java-app"
          operationName "http.request"
          resourceName "GET /"
          errored false
          tags {
            defaultTags()
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "http://localhost:$port/"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.getAddress().port
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
          }
        }
      }
    }
  }

  def "trace request without propagation"() {
    setup:
    final HttpClientBuilder builder = HttpClientBuilder.create()

    final HttpClient client = builder.build()
    runUnderTrace("someTrace") {
      try {
        HttpGet request = new HttpGet(server.getAddress())
        request.addHeader(new BasicHeader("is-dd-server", "false"))
        HttpResponse response = client.execute(request)
        assert response.getStatusLine().getStatusCode() == 200
      } catch (Exception e) {
        e.printStackTrace()
        throw new RuntimeException(e)
      }
    }
    expect:
    // only one trace (client).
    assertTraces(TEST_WRITER, 1) {
      trace(0, 3) {
        span(0) {
          parent()
          serviceName "unnamed-java-app"
          operationName "someTrace"
          resourceName "someTrace"
          errored false
          tags {
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName "unnamed-java-app"
          operationName "apache.http"
          resourceName "apache.http"
          errored false
          tags {
            defaultTags()
            "$Tags.COMPONENT.key" "apache-httpclient"
          }
        }
        span(2) {
          childOf span(1)
          serviceName "unnamed-java-app"
          operationName "http.request"
          resourceName "GET /"
          errored false
          tags {
            defaultTags()
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "http://localhost:$port/"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.getAddress().port
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
          }
        }
      }
    }
  }
}
