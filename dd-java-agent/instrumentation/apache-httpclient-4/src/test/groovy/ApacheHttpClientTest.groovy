import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.apachehttpclient.ApacheHttpClientDecorator
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.message.BasicHeader
import spock.lang.Shared

class ApacheHttpClientTest extends HttpClientTest<ApacheHttpClientDecorator> {

  @Shared
  def client = new DefaultHttpClient()

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def request = new HttpUriRequest(method, uri)
    headers.entrySet().each {
      request.addHeader(new BasicHeader(it.key, it.value))
    }

    def response = client.execute(request)
    callback?.call()
    response.entity?.content?.close() // Make sure the connection is closed.

    return response.statusLine.statusCode
  }

  @Override
  ApacheHttpClientDecorator decorator() {
    return ApacheHttpClientDecorator.DECORATE
  }
}
