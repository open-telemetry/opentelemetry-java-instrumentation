package dd.trace.instrumentation.springwebflux.client

import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import datadog.trace.bootstrap.instrumentation.api.Tags
import datadog.trace.instrumentation.netty41.client.NettyHttpClientDecorator
import datadog.trace.instrumentation.springwebflux.client.SpringWebfluxHttpClientDecorator
import org.springframework.http.HttpMethod
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import spock.lang.Shared
import spock.lang.Timeout

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan

@Timeout(5)
class SpringWebfluxHttpClientTest extends HttpClientTest {

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
  String component() {
    return SpringWebfluxHttpClientDecorator.DECORATE.component()
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
          defaultTags()
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

  boolean testRemoteConnection() {
    // FIXME: figure out how to configure timeouts.
    false
  }
}
