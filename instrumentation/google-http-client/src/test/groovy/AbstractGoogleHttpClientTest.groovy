import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpResponse
import com.google.api.client.http.javanet.NetHttpTransport
import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.instrumentation.googlehttpclient.GoogleHttpClientDecorator
import io.opentelemetry.auto.test.base.HttpClientTest
import spock.lang.Shared

abstract class AbstractGoogleHttpClientTest extends HttpClientTest<GoogleHttpClientDecorator> {

  @Shared
  def requestFactory = new NetHttpTransport().createRequestFactory()

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    doRequest(method, uri, headers, callback, false)
  }

  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback, boolean throwExceptionOnError) {
    GenericUrl genericUrl = new GenericUrl(uri)

    HttpRequest request = requestFactory.buildRequest(method, genericUrl, null)
    request.getHeaders().putAll(headers)
    request.setThrowExceptionOnExecuteError(throwExceptionOnError)

    HttpResponse response = executeRequest(request)
    callback?.call()

    return response.getStatusCode()
  }

  abstract HttpResponse executeRequest(HttpRequest request)

  @Override
  GoogleHttpClientDecorator decorator() {
    return GoogleHttpClientDecorator.DECORATE
  }

  @Override
  boolean testCircularRedirects() {
    // Circular redirects don't throw an exception with Google Http Client
    return false
  }

  def "error traces when exception is not thrown"() {
    given:
    def uri = server.address.resolve("/error")

    when:
    def status = doRequest(method, uri)

    then:
    status == 500
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          errored true
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "google-http-client"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.PEER_HOSTNAME" "localhost"
            "$Tags.PEER_PORT" Long
            "$Tags.HTTP_URL" "${uri.resolve(uri.path)}"
            "$Tags.HTTP_METHOD" method
            "$Tags.HTTP_STATUS" 500
            "$MoreTags.ERROR_MSG" "Server Error"
          }
        }
        server.distributedRequestSpan(it, 1, span(0))
      }
    }

    where:
    method = "GET"
  }
}
