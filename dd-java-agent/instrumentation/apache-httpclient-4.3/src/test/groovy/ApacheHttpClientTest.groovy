import datadog.opentracing.DDSpan
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import io.opentracing.Scope
import io.opentracing.SpanContext
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMap
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.message.BasicHeader
import ratpack.handling.Context
import spock.lang.Shared

import static datadog.trace.agent.test.TestUtils.runUnderTrace
import static ratpack.groovy.test.embed.GroovyEmbeddedApp.ratpack

class ApacheHttpClientTest extends AgentTestRunner {

  @Shared
  def server = ratpack {
    handlers {
      get {
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
              .asChildOf(extractedContext)
              .startActive(true)
          scope.close()
        }

        response.status(200).send(msg)
      }
    }
  }

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
    TEST_WRITER.size() == 2
    final List<DDSpan> serverTrace = TEST_WRITER.get(0)
    serverTrace.size() == 1

    final List<DDSpan> clientTrace = TEST_WRITER.get(1)
    clientTrace.size() == 3
    clientTrace.get(0).getOperationName() == "someTrace"
    // our instrumentation makes 2 spans for apache-httpclient
    final DDSpan localSpan = clientTrace.get(1)
    localSpan.getType() == null
    localSpan.getTags()[Tags.COMPONENT.getKey()] == "apache-httpclient"
    localSpan.getOperationName() == "apache.http"

    final DDSpan clientSpan = clientTrace.get(2)
    clientSpan.getOperationName() == "http.request"
    clientSpan.getType() == DDSpanTypes.HTTP_CLIENT
    clientSpan.getTags()[Tags.HTTP_METHOD.getKey()] == "GET"
    clientSpan.getTags()[Tags.HTTP_STATUS.getKey()] == 200
    clientSpan.getTags()[Tags.HTTP_URL.getKey()] == server.getAddress().toString()
    clientSpan.getTags()[Tags.PEER_HOSTNAME.getKey()] == "localhost"
    clientSpan.getTags()[Tags.PEER_PORT.getKey()] == server.getAddress().port
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
    TEST_WRITER.size() == 1
    final List<DDSpan> clientTrace = TEST_WRITER.get(0)
    clientTrace.size() == 3
    clientTrace.get(0).getOperationName() == "someTrace"
    // our instrumentation makes 2 spans for apache-httpclient
    final DDSpan localSpan = clientTrace.get(1)
    localSpan.getTags()[Tags.COMPONENT.getKey()] == "apache-httpclient"
    localSpan.getOperationName() == "apache.http"

    final DDSpan clientSpan = clientTrace.get(2)
    clientSpan.getOperationName() == "http.request"
    clientSpan.getTags()[Tags.HTTP_METHOD.getKey()] == "GET"
    clientSpan.getTags()[Tags.HTTP_STATUS.getKey()] == 200
    clientSpan.getTags()[Tags.HTTP_URL.getKey()] == server.getAddress().toString()
    clientSpan.getTags()[Tags.PEER_HOSTNAME.getKey()] == "localhost"
    clientSpan.getTags()[Tags.PEER_PORT.getKey()] == server.getAddress().port
    clientSpan.getTags()[Tags.SPAN_KIND.getKey()] == Tags.SPAN_KIND_CLIENT
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
