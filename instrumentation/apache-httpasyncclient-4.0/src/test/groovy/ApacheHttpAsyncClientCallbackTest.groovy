import io.opentelemetry.auto.instrumentation.apachehttpasyncclient.ApacheHttpAsyncClientDecorator
import io.opentelemetry.auto.test.base.HttpClientTest
import org.apache.http.HttpResponse
import org.apache.http.concurrent.FutureCallback
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.apache.http.message.BasicHeader
import spock.lang.AutoCleanup
import spock.lang.Shared

import java.util.concurrent.CompletableFuture

class ApacheHttpAsyncClientCallbackTest extends HttpClientTest<ApacheHttpAsyncClientDecorator> {

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

    def responseFuture = new CompletableFuture<>()

    client.execute(request, new FutureCallback<HttpResponse>() {

      @Override
      void completed(HttpResponse result) {
        callback?.call()
        responseFuture.complete(result.statusLine.statusCode)
      }

      @Override
      void failed(Exception ex) {
        responseFuture.completeExceptionally(ex)
      }

      @Override
      void cancelled() {
        responseFuture.cancel(true)
      }
    })

    return responseFuture.get()
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
