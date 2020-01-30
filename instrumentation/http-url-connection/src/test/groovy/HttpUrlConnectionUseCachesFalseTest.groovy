import io.opentelemetry.auto.instrumentation.http_url_connection.HttpUrlConnectionDecorator
import io.opentelemetry.auto.test.base.HttpClientTest

class HttpUrlConnectionUseCachesFalseTest extends HttpClientTest<HttpUrlConnectionDecorator> {

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    HttpURLConnection connection = uri.toURL().openConnection()
    try {
      connection.setRequestMethod(method)
      headers.each { connection.setRequestProperty(it.key, it.value) }
      connection.setRequestProperty("Connection", "close")
      connection.useCaches = false
      def parentSpan = TEST_TRACER.getCurrentSpan()
      def stream = connection.inputStream
      assert TEST_TRACER.getCurrentSpan() == parentSpan
      stream.readLines()
      stream.close()
      callback?.call()
      return connection.getResponseCode()
    } finally {
      connection.disconnect()
    }
  }

  @Override
  HttpUrlConnectionDecorator decorator() {
    return HttpUrlConnectionDecorator.DECORATE
  }

  @Override
  boolean testCircularRedirects() {
    false
  }
}
