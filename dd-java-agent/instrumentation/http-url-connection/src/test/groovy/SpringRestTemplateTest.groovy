import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.http_url_connection.HttpUrlConnectionDecorator
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import spock.lang.Shared
import spock.lang.Timeout

@Timeout(5)
class SpringRestTemplateTest extends HttpClientTest {

  @Shared
  ClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory()
  @Shared
  RestTemplate restTemplate = new RestTemplate(factory)

  def setupSpec() {
    factory.connectTimeout = CONNECT_TIMEOUT_MS
    factory.readTimeout = READ_TIMEOUT_MS
  }

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
  String component() {
    return HttpUrlConnectionDecorator.DECORATE.component()
  }

  @Override
  boolean testCircularRedirects() {
    false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }

  @Override
  boolean testRemoteConnection() {
    // FIXME: exception wrapped in ResourceAccessException
    return false
  }
}
