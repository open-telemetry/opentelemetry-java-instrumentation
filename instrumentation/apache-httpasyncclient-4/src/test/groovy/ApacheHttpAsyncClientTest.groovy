import io.opentelemetry.auto.instrumentation.apachehttpasyncclient.ApacheHttpAsyncClientDecorator
import io.opentelemetry.auto.test.base.HttpClientTest
import org.apache.http.HttpResponse
import org.apache.http.concurrent.FutureCallback
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.apache.http.message.BasicHeader
import spock.lang.AutoCleanup
import spock.lang.Shared

import java.util.concurrent.CountDownLatch

class ApacheHttpAsyncClientTest extends HttpClientTest<ApacheHttpAsyncClientDecorator> {

  @AutoCleanup
  @Shared
  def client = HttpAsyncClients.createDefault()

  def setupSpec() {
    client.start()
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def request = new HttpUriRequest(method, uri)
    headers.entrySet().each {
      request.addHeader(new BasicHeader(it.key, it.value))
    }

    def latch = callback == null ? null : new CountDownLatch(1)
    def handler = callback == null ? null : new FutureCallback<HttpResponse>() {

      @Override
      void completed(HttpResponse result) {
        callback()
        latch.countDown()
      }

      @Override
      void failed(Exception ex) {
        latch.countDown()
      }

      @Override
      void cancelled() {
        latch.countDown()
      }
    }

    def future = client.execute(request, handler)
    def response = future.get()
    response.entity?.content?.close() // Make sure the connection is closed.
    if (callback != null) {
      // need to wait for callback to complete in case test is expecting span from it
      latch.await()
    }
    response.statusLine.statusCode
  }

  @Override
  ApacheHttpAsyncClientDecorator decorator() {
    return ApacheHttpAsyncClientDecorator.DECORATE
  }

  @Override
  Integer statusOnRedirectError() {
    return 302
  }
}
