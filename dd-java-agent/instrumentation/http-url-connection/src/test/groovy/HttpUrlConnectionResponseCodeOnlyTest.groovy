import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.http_url_connection.HttpUrlConnectionDecorator

class HttpUrlConnectionResponseCodeOnlyTest extends HttpClientTest<HttpUrlConnectionDecorator> {

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    HttpURLConnection connection = uri.toURL().openConnection()
    try {
      connection.setRequestMethod(method)
      headers.each { connection.setRequestProperty(it.key, it.value) }
      connection.setRequestProperty("Connection", "close")
      return connection.getResponseCode()
    } finally {
      callback?.call()
      connection.disconnect()
    }
  }

  @Override
  HttpUrlConnectionDecorator decorator() {
    return HttpUrlConnectionDecorator.DECORATE
  }

  @Override
  boolean testRedirects() {
    false
  }
}
