import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.ListWriterAssert
import datadog.trace.agent.test.RatpackUtils
import datadog.trace.agent.test.TraceAssert
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.config.RequestConfig
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
      prefix("success") {
        get {
          RatpackUtils.handleDistributedRequest(context)

          String msg = "<html><body><h1>Hello test.</h1>\n"
          response.status(200).send(msg)
        }
      }
      prefix("redirect") {
        get {
          RatpackUtils.handleDistributedRequest(context)

          redirect(server.address.resolve("/success").toURL().toString())
        }
      }
    }
  }
  @Shared
  int port = server.address.port
  @Shared
  def successUrl = server.address.resolve("/success")
  @Shared
  def redirectUrl = server.address.resolve("/redirect")

  final HttpClientBuilder builder = HttpClientBuilder.create()
  final HttpClient client = builder.build()

  def "trace request with propagation"() {
    setup:
    runUnderTrace("someTrace") {
      HttpResponse response = client.execute(new HttpGet(successUrl))
      assert response.getStatusLine().getStatusCode() == 200
    }

    expect:
    // one trace on the server, one trace on the client
    assertTraces(TEST_WRITER, 2) {
      serverTrace(it, 0, TEST_WRITER[1][2])
      trace(1, 3) {
        outerSpan(it, 0)
        clientParentSpan(it, 1, span(0))
        successClientSpan(it, 2, span(1))
      }
    }
  }

  def "trace redirected request with propagation many redirects allowed"() {
    setup:
    final RequestConfig.Builder requestConfigBuilder = new RequestConfig.Builder()
    requestConfigBuilder.setMaxRedirects(10)
    runUnderTrace("someTrace") {
      HttpGet request = new HttpGet(redirectUrl)
      request.setConfig(requestConfigBuilder.build())
      HttpResponse response = client.execute(request)
      assert response.getStatusLine().getStatusCode() == 200
    }

    expect:
    // two traces on the server, one trace on the client
    assertTraces(TEST_WRITER, 3) {
      serverTrace(it, 0, TEST_WRITER[2][3])
      serverTrace(it, 1, TEST_WRITER[2][2])
      trace(2, 4) {
        outerSpan(it, 0)
        clientParentSpan(it, 1, span(0))
        successClientSpan(it, 2, span(1))
        redirectClientSpan(it, 3, span(1))
      }
    }
  }

  def "trace redirected request with propagation 1 redirect allowed"() {
    setup:
    final RequestConfig.Builder requestConfigBuilder = new RequestConfig.Builder()
    requestConfigBuilder.setMaxRedirects(1)
    runUnderTrace("someTrace") {
      HttpGet request = new HttpGet(redirectUrl)
      request.setConfig(requestConfigBuilder.build())
      HttpResponse response = client.execute(request)
      assert response.getStatusLine().getStatusCode() == 200
    }

    expect:
    print(TEST_WRITER)
    // two traces on the server, one trace on the client
    assertTraces(TEST_WRITER, 3) {
      serverTrace(it, 0, TEST_WRITER[2][3])
      serverTrace(it, 1, TEST_WRITER[2][1])
      trace(2, 4) {
        outerSpan(it, 0)
        // Note: this is kind of an weird order?
        successClientSpan(it, 1, span(2))
        clientParentSpan(it, 2, span(0))
        redirectClientSpan(it, 3, span(2))
      }
    }
  }

  def "trace request without propagation"() {
    setup:
    runUnderTrace("someTrace") {
      HttpGet request = new HttpGet(successUrl)
      request.addHeader(new BasicHeader("is-dd-server", "false"))
      HttpResponse response = client.execute(request)
      assert response.getStatusLine().getStatusCode() == 200
    }
    expect:
    // only one trace (client).
    assertTraces(TEST_WRITER, 1) {
      trace(0, 3) {
        outerSpan(it, 0)
        clientParentSpan(it, 1, span(0))
        successClientSpan(it, 2, span(1))
      }
    }
  }

  def serverTrace(ListWriterAssert writer, index, parent) {
    writer.trace(index, 1) {
      span(0) {
        childOf parent
        serviceName "unnamed-java-app"
        operationName "test-http-server"
        resourceName "test-http-server"
        errored false
        tags {
          defaultTags()
        }
      }
    }
  }

  def outerSpan(TraceAssert trace, index) {
    trace.span(index) {
      parent()
      serviceName "unnamed-java-app"
      operationName "someTrace"
      resourceName "someTrace"
      errored false
      tags {
        defaultTags()
      }
    }
  }

  def clientParentSpan(TraceAssert trace, index, parent) {
    trace.span(index) {
      childOf parent
      serviceName "unnamed-java-app"
      operationName "apache.http"
      resourceName "apache.http"
      errored false
      tags {
        defaultTags()
        "$Tags.COMPONENT.key" "apache-httpclient"
      }
    }
  }

  def successClientSpan(TraceAssert trace, index, parent, status = 200, route = "success") {
    trace.span(index) {
      childOf parent
      serviceName "unnamed-java-app"
      operationName "http.request"
      resourceName "GET /$route"
      errored false
      tags {
        defaultTags()
        "$Tags.HTTP_STATUS.key" status
        "$Tags.HTTP_URL.key" "http://localhost:$port/$route"
        "$Tags.PEER_HOSTNAME.key" "localhost"
        "$Tags.PEER_PORT.key" server.getAddress().port
        "$Tags.HTTP_METHOD.key" "GET"
        "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
        "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
      }
    }
  }

  def redirectClientSpan(TraceAssert trace, index, parent) {
    successClientSpan(trace, index, parent, 302, "redirect")
  }
}
