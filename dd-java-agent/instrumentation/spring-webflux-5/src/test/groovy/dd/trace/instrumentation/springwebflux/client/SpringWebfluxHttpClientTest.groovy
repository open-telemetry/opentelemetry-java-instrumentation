package dd.trace.instrumentation.springwebflux.client

import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import datadog.trace.instrumentation.api.Tags
import datadog.trace.instrumentation.netty41.client.NettyHttpClientDecorator
import datadog.trace.instrumentation.springwebflux.client.SpringWebfluxHttpClientDecorator
import org.springframework.http.HttpMethod
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import spock.lang.Ignore
import spock.lang.Shared

import static datadog.trace.instrumentation.api.AgentTracer.activeSpan

// FIXME this instrumentation is not currently reliable and so is currently disabled
// see DefaultWebClientInstrumentation and DefaultWebClientAdvice
@Ignore
class SpringWebfluxHttpClientTest extends HttpClientTest<SpringWebfluxHttpClientDecorator> {

  @Shared
  def client = WebClient.builder().build()

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def hasParent = activeSpan() != null
    ClientResponse response = client.method(HttpMethod.resolve(method))
      .headers { h -> headers.forEach({ key, value -> h.add(key, value) }) }
      .uri(uri)
      .exchange()
      .doOnSuccessOrError { success, error ->
        blockUntilChildSpansFinished(1)
        callback?.call()
      }
      .block()

    if (hasParent) {
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
        operationName "netty.client.request"
        errored exception != null
        tags {
          "$DDTags.SERVICE_NAME" renameService ? "localhost" : null
          "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
          "$Tags.COMPONENT" NettyHttpClientDecorator.DECORATE.component()
          "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          "$Tags.PEER_HOSTNAME" "localhost"
          "$Tags.PEER_PORT" uri.port
          "$Tags.PEER_HOST_IPV4" { it == null || it == "127.0.0.1" } // Optional
          "$Tags.HTTP_URL" "${uri.resolve(uri.path)}"
          "$Tags.HTTP_METHOD" method
          if (status) {
            "$Tags.HTTP_STATUS" status
          }
          if (tagQueryString) {
            "$DDTags.HTTP_QUERY" uri.query
            "$DDTags.HTTP_FRAGMENT" { it == null || it == uri.fragment } // Optional
          }
          if (exception) {
            errorTags(exception.class, exception.message)
          }
        }
      }
    }
  }

  @Override
  int extraClientSpans() {
    // has netty-client span inside of spring-webflux-client
    return 1
  }

  boolean testRedirects() {
    false
  }

  boolean testConnectionFailure() {
    false
  }
}
