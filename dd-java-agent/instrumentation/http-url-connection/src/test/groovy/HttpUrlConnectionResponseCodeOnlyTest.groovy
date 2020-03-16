import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.http_url_connection.HttpUrlConnectionDecorator
import spock.lang.Timeout

@Timeout(5)
class HttpUrlConnectionResponseCodeOnlyTest extends HttpClientTest {

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    HttpURLConnection connection = uri.toURL().openConnection()
    try {
      connection.setRequestMethod(method)
      connection.connectTimeout = CONNECT_TIMEOUT_MS
      connection.readTimeout = READ_TIMEOUT_MS
      headers.each { connection.setRequestProperty(it.key, it.value) }
      connection.setRequestProperty("Connection", "close")
      return connection.getResponseCode()
    } finally {
      callback?.call()
      connection.disconnect()
    }
  }

  @Override
  String component() {
    return HttpUrlConnectionDecorator.DECORATE.component()
  }

  @Override
  boolean testCircularRedirects() {
    false
  }
}
