import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.http_url_connection.HttpUrlConnectionDecorator
import io.opentracing.util.GlobalTracer

class HttpUrlConnectionUseCachesFalseTest extends HttpClientTest<HttpUrlConnectionDecorator> {

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    HttpURLConnection connection = uri.toURL().openConnection()
    try {
      connection.setRequestMethod(method)
      headers.each { connection.setRequestProperty(it.key, it.value) }
      connection.setRequestProperty("Connection", "close")
      connection.useCaches = false
      def parentSpan = GlobalTracer.get().scopeManager().active()
      def stream = connection.inputStream
      assert GlobalTracer.get().scopeManager().active() == parentSpan
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
  boolean testRedirects() {
    false
  }
}
