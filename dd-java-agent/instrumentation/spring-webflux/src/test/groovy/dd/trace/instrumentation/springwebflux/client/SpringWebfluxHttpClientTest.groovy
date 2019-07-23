package dd.trace.instrumentation.springwebflux.client

import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import datadog.trace.instrumentation.netty41.client.NettyHttpClientDecorator
import datadog.trace.instrumentation.springwebflux.client.SpringWebfluxHttpClientDecorator
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer
import org.springframework.http.HttpMethod
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import spock.lang.Shared

class SpringWebfluxHttpClientTest extends HttpClientTest<SpringWebfluxHttpClientDecorator> {

  @Shared
  def client = WebClient.builder().build()

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def hasParent = GlobalTracer.get().activeSpan() != null
    ClientResponse response = client.method(HttpMethod.resolve(method))
      .headers { h -> headers.forEach({ key, value -> h.add(key, value) }) }
      .uri(uri)
      .exchange()
      .doOnSuccessOrError { success, error ->
        blockUntilChildSpansFinished(1)
        callback?.call()
      }
      .block()

    if(hasParent) {
      blockUntilChildSpansFinished(callback ? 3 : 2)
    }
    response.statusCode().value()
  }

  @Override
  SpringWebfluxHttpClientDecorator decorator() {
    return SpringWebfluxHttpClientDecorator.DECORATE
  }


  @Override
  // parent spanRef must be cast otherwise it breaks debugging classloading (junit loads it early)
  void clientSpan(TraceAssert trace, int index, Object parentSpan, String method = "GET", boolean renameService = false, boolean tagQueryString = false, URI uri = server.address.resolve("/success"), Integer status = 200, Throwable exception = null) {
    super.clientSpan(trace, index, parentSpan, method, renameService, tagQueryString, uri, status, exception)
    if (!exception) {
      trace.span(index + 1) {
        childOf(trace.span(index))
        serviceName renameService ? "localhost" : "unnamed-java-app"
        operationName "netty.client.request"
        resourceName "$method $uri.path"
        spanType DDSpanTypes.HTTP_CLIENT
        errored exception != null
        tags {
          defaultTags()
          if (exception) {
            errorTags(exception.class, exception.message)
          }
          "$Tags.COMPONENT.key" NettyHttpClientDecorator.DECORATE.component()
          if (status) {
            "$Tags.HTTP_STATUS.key" status
          }
          "$Tags.HTTP_URL.key" "${uri.resolve(uri.path)}"
          if (tagQueryString) {
            "$DDTags.HTTP_QUERY" uri.query
            "$DDTags.HTTP_FRAGMENT" { it == null || it == uri.fragment } // Optional
          }
          "$Tags.PEER_HOSTNAME.key" "localhost"
          "$Tags.PEER_PORT.key" uri.port
          "$Tags.PEER_HOST_IPV4.key" { it == null || it == "127.0.0.1" } // Optional
          "$Tags.HTTP_METHOD.key" method
          "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
        }
      }
    }
  }

  @Override
  int size(int size) {
    return size + 1
  }

  boolean testRedirects() {
    false
  }

  boolean testConnectionFailure() {
    false
  }
}
