import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.http_url_connection.HttpUrlConnectionDecorator
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import spock.lang.Shared

class SpringRestTemplateTest extends HttpClientTest<HttpUrlConnectionDecorator> {

  @Shared
  RestTemplate restTemplate = new RestTemplate()

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def httpHeaders = new HttpHeaders()
    headers.each { httpHeaders.put(it.key, [it.value]) }
    def request = new HttpEntity<String>(httpHeaders)
    ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.resolve(method), request, String)
    callback?.call()
    return response.statusCode.value()
  }

  @Override
  HttpUrlConnectionDecorator decorator() {
    return HttpUrlConnectionDecorator.DECORATE
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }
}
