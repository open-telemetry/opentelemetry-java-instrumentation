import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.apachehttpasyncclient.ApacheHttpAsyncClientDecorator
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.apache.http.message.BasicHeader
import spock.lang.AutoCleanup
import spock.lang.Shared

import java.util.concurrent.Future

class ApacheHttpAsyncClientNullCallbackTest extends HttpClientTest<ApacheHttpAsyncClientDecorator> {

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

    // The point here is to test case when callback is null - fire-and-forget style
    // So to make sure request is done we start request, wait for future to finish
    // and then call callback if present.
    Future future = client.execute(request, null)
    future.get()
    if (callback != null) {
      blockUntilChildSpansFinished(1)
      callback()
    }
    return 200
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
