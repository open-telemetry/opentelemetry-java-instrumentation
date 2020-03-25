import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.http_url_connection.HttpUrlConnectionDecorator
import spock.lang.Timeout

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeScope

@Timeout(5)
class HttpUrlConnectionUseCachesFalseTest extends HttpClientTest {

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    HttpURLConnection connection = uri.toURL().openConnection()
    try {
      connection.setRequestMethod(method)
      headers.each { connection.setRequestProperty(it.key, it.value) }
      connection.setRequestProperty("Connection", "close")
      connection.useCaches = false
      connection.connectTimeout = CONNECT_TIMEOUT_MS
      connection.readTimeout = READ_TIMEOUT_MS
      def parentSpan = activeScope()
      def stream = connection.inputStream
      assert activeScope() == parentSpan
      stream.readLines()
      stream.close()
      callback?.call()
      return connection.getResponseCode()
    } finally {
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
