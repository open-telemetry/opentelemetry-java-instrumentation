package datadog.trace.agent.integration.httpclient

import datadog.opentracing.DDSpan
import datadog.opentracing.DDTracer
import datadog.trace.agent.integration.TestHttpServer
import datadog.trace.agent.test.IntegrationTestUtils
import datadog.trace.api.DDSpanTypes
import datadog.trace.common.writer.ListWriter
import io.opentracing.tag.Tags
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.message.BasicHeader
import spock.lang.Shared
import spock.lang.Specification

class ApacheHttpClientTest extends Specification {

  @Shared
  def writer = new ListWriter()
  @Shared
  def tracer = new DDTracer(writer)

  def setupSpec() {
    IntegrationTestUtils.registerOrReplaceGlobalTracer(tracer)
    TestHttpServer.startServer()
  }

  def cleanupSpec() {
    TestHttpServer.stopServer()
  }

  def setup() {
    writer.clear()
  }

  def "trace request with propagation"() {
    setup:
    final HttpClientBuilder builder = HttpClientBuilder.create()

    final HttpClient client = builder.build()
    IntegrationTestUtils.runUnderTrace("someTrace") {
      try {
        HttpResponse response =
          client.execute(new HttpGet(new URI("http://localhost:" + TestHttpServer.getPort())))
        assert response.getStatusLine().getStatusCode() == 200
      } catch (Exception e) {
        e.printStackTrace()
        throw new RuntimeException(e)
      }
    }

    expect:
    // one trace on the server, one trace on the client
    writer.size() == 2
    final List<DDSpan> serverTrace = writer.get(0)
    serverTrace.size() == 1

    final List<DDSpan> clientTrace = writer.get(1)
    clientTrace.size() == 3
    clientTrace.get(0).getOperationName() == "someTrace"
    // our instrumentation makes 2 spans for apache-httpclient
    final DDSpan localSpan = clientTrace.get(1)
    localSpan.getType() == null
    localSpan.getTags()[Tags.COMPONENT.getKey()] == "apache-httpclient"
    localSpan.getOperationName() == "GET"

    final DDSpan clientSpan = clientTrace.get(2)
    clientSpan.getOperationName() == "GET"
    clientSpan.getType() == DDSpanTypes.HTTP_CLIENT
    clientSpan.getTags()[Tags.HTTP_METHOD.getKey()] == "GET"
    clientSpan.getTags()[Tags.HTTP_STATUS.getKey()] == 200
    clientSpan.getTags()[Tags.HTTP_URL.getKey()] == "http://localhost:" + TestHttpServer.getPort()
    clientSpan.getTags()[Tags.PEER_HOSTNAME.getKey()] == "localhost"
    clientSpan.getTags()[Tags.PEER_PORT.getKey()] == TestHttpServer.getPort()
    clientSpan.getTags()[Tags.SPAN_KIND.getKey()] == Tags.SPAN_KIND_CLIENT

    // client trace propagates to server
    clientSpan.getTraceId() == serverTrace.get(0).getTraceId()
    // server span is parented under http client
    clientSpan.getSpanId() == serverTrace.get(0).getParentId()
  }


  def "trace request without propagation"() {
    setup:
    final HttpClientBuilder builder = HttpClientBuilder.create()

    final HttpClient client = builder.build()
    IntegrationTestUtils.runUnderTrace("someTrace") {
      try {
        HttpGet request = new HttpGet(new URI("http://localhost:"
          + TestHttpServer.getPort()))
        request.addHeader(new BasicHeader(TestHttpServer.IS_DD_SERVER, "false"))
        HttpResponse response = client.execute(request)
        assert response.getStatusLine().getStatusCode() == 200
      } catch (Exception e) {
        e.printStackTrace()
        throw new RuntimeException(e)
      }
    }
    expect:
    // only one trace (client).
    writer.size() == 1
    final List<DDSpan> clientTrace = writer.get(0)
    clientTrace.size() == 3
    clientTrace.get(0).getOperationName() == "someTrace"
    // our instrumentation makes 2 spans for apache-httpclient
    final DDSpan localSpan = clientTrace.get(1)
    localSpan.getTags()[Tags.COMPONENT.getKey()] == "apache-httpclient"
    localSpan.getOperationName() == "GET"

    final DDSpan clientSpan = clientTrace.get(2)
    clientSpan.getOperationName() == "GET"
    clientSpan.getTags()[Tags.HTTP_METHOD.getKey()] == "GET"
    clientSpan.getTags()[Tags.HTTP_STATUS.getKey()] == 200
    clientSpan.getTags()[Tags.HTTP_URL.getKey()] == "http://localhost:" + TestHttpServer.getPort()
    clientSpan.getTags()[Tags.PEER_HOSTNAME.getKey()] == "localhost"
    clientSpan.getTags()[Tags.PEER_PORT.getKey()] == TestHttpServer.getPort()
    clientSpan.getTags()[Tags.SPAN_KIND.getKey()] == Tags.SPAN_KIND_CLIENT
  }

}
