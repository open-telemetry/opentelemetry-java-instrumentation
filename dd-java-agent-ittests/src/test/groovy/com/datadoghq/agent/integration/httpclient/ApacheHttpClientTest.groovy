package com.datadoghq.agent.integration.httpclient

import com.datadoghq.agent.integration.TestUtils
import com.datadoghq.agent.integration.TestHttpServer
import com.datadoghq.trace.DDBaseSpan
import com.datadoghq.trace.DDTracer
import com.datadoghq.trace.writer.ListWriter
import io.opentracing.tag.Tags
import java.net.URI
import java.util.List
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import spock.lang.*

class ApacheHttpClientTest extends Specification {

  @Shared
  def ListWriter writer = new ListWriter()
  @Shared
  def DDTracer tracer = new DDTracer(writer)

  def setupSpec() {
    TestUtils.registerOrReplaceGlobalTracer(tracer)
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
    TestUtils.runUnderTrace(
        "someTrace",
        new Runnable() {
          @Override
          public void run() {
            try {
              HttpResponse response =
                  client.execute(new HttpGet(new URI("http://localhost:" + TestHttpServer.PORT)))
              assert response.getStatusLine().getStatusCode() == 200
            } catch (Exception e) {
              e.printStackTrace()
              throw new RuntimeException(e)
            }
          }
        })
    expect:
    // one trace on the server, one trace on the client
    writer.size() == 2
    final List<DDBaseSpan<?>> serverTrace = writer.get(0)
    serverTrace.size() == 1

    final List<DDBaseSpan<?>> clientTrace = writer.get(1)
    clientTrace.size() == 3
    clientTrace.get(0).getOperationName() == "someTrace"
    // our instrumentation makes 2 spans for apache-httpclient
    final DDBaseSpan<?> localSpan = clientTrace.get(1)
    localSpan.getTags()[Tags.COMPONENT.getKey()] == "apache-httpclient"
    localSpan.getOperationName() == "GET"

    final DDBaseSpan<?> clientSpan = clientTrace.get(2)
    clientSpan.getOperationName() == "GET"
    clientSpan.getTags()[Tags.HTTP_METHOD.getKey()] == "GET"
    clientSpan.getTags()[Tags.HTTP_STATUS.getKey()] == 200
    clientSpan.getTags()[Tags.HTTP_URL.getKey()] == "http://localhost:8089"
    clientSpan.getTags()[Tags.PEER_HOSTNAME.getKey()] == "localhost"
    clientSpan.getTags()[Tags.PEER_PORT.getKey()] == 8089
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
    TestUtils.runUnderTrace(
        "someTrace",
        new Runnable() {
          @Override
          public void run() {
            try {
              HttpResponse response =
                  client.execute(new HttpGet(new URI("http://localhost:"
                                                     + TestHttpServer.PORT
                                                     + "?"
                                                     + TestHttpServer.IS_DD_SERVER
                                                     + "=false")))
              assert response.getStatusLine().getStatusCode() == 200
            } catch (Exception e) {
              e.printStackTrace()
              throw new RuntimeException(e)
            }
          }
        })
    expect:
    // only one trace (client).
    writer.size() == 1
    final List<DDBaseSpan<?>> clientTrace = writer.get(0)
    clientTrace.size() == 3
    clientTrace.get(0).getOperationName() == "someTrace"
    // our instrumentation makes 2 spans for apache-httpclient
    final DDBaseSpan<?> localSpan = clientTrace.get(1)
    localSpan.getTags()[Tags.COMPONENT.getKey()] == "apache-httpclient"
    localSpan.getOperationName() == "GET"

    final DDBaseSpan<?> clientSpan = clientTrace.get(2)
    clientSpan.getOperationName() == "GET"
    clientSpan.getTags()[Tags.HTTP_METHOD.getKey()] == "GET"
    clientSpan.getTags()[Tags.HTTP_STATUS.getKey()] == 200
    clientSpan.getTags()[Tags.HTTP_URL.getKey()] == "http://localhost:8089?is-dd-server=false"
    clientSpan.getTags()[Tags.PEER_HOSTNAME.getKey()] == "localhost"
    clientSpan.getTags()[Tags.PEER_PORT.getKey()] == 8089
    clientSpan.getTags()[Tags.SPAN_KIND.getKey()] == Tags.SPAN_KIND_CLIENT
  }

}
