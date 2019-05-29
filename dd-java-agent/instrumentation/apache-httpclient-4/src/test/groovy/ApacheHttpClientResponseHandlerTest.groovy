import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.apachehttpclient.ApacheHttpClientDecorator
import org.apache.http.HttpResponse
import org.apache.http.client.ResponseHandler
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.message.BasicHeader
import spock.lang.Shared

class ApacheHttpClientResponseHandlerTest extends HttpClientTest<ApacheHttpClientDecorator> {

  @Shared
  def client = new DefaultHttpClient()

  @Shared
  def handler = new ResponseHandler<Integer>() {
    @Override
    Integer handleResponse(HttpResponse response) {
      return response.statusLine.statusCode
    }
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def request = new HttpUriRequest(method, uri)
    headers.entrySet().each {
      request.addHeader(new BasicHeader(it.key, it.value))
    }

    def status = client.execute(request, handler)

    // handler execution is included within the client span, so we can't call the callback there.
    callback?.call()

    return status
  }

  @Override
  ApacheHttpClientDecorator decorator() {
    return ApacheHttpClientDecorator.DECORATE
  }
}
