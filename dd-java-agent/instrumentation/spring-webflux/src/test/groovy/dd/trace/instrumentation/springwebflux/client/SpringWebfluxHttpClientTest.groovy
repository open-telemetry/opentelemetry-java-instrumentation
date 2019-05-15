package dd.trace.instrumentation.springwebflux.client

import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.springwebflux.client.SpringWebfluxHttpClientDecorator
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import spock.lang.Shared

class SpringWebfluxHttpClientTest extends HttpClientTest<SpringWebfluxHttpClientDecorator> {

  @Shared
  def client = WebClient.builder().build()

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    assert method == "GET"
    ClientResponse response = client.get()
      .headers({ h -> headers.forEach({ key, value -> h.add(key, value) }) })
      .uri(uri)
      .exchange()
      .block()

    callback?.call()
    response.statusCode().value()
  }

  @Override
  SpringWebfluxHttpClientDecorator decorator() {
    return SpringWebfluxHttpClientDecorator.DECORATE
  }
}
