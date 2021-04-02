/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.concurrent.CancellationException
import java.util.function.Consumer
import org.apache.http.HttpResponse
import org.apache.http.client.config.RequestConfig
import org.apache.http.concurrent.FutureCallback
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.apache.http.message.BasicHeader
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Timeout

@Timeout(5)
class ApacheHttpAsyncClientTest extends HttpClientTest implements AgentTestTrait {

  @Shared
  RequestConfig requestConfig = RequestConfig.custom()
    .setConnectTimeout(CONNECT_TIMEOUT_MS)
    .build()

  @AutoCleanup
  @Shared
  def client = HttpAsyncClients.custom().setDefaultRequestConfig(requestConfig).build()

  def setupSpec() {
    client.start()
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers = [:]) {
    return client.execute(buildRequest(method, uri, headers), null).get().statusLine.statusCode
  }

  @Override
  void doRequestAsync(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    client.execute(buildRequest(method, uri, headers), new FutureCallback<HttpResponse>() {
      @Override
      void completed(HttpResponse httpResponse) {
        callback.accept(httpResponse.statusLine.statusCode)
      }

      @Override
      void failed(Exception e) {
        throw e
      }

      @Override
      void cancelled() {
        throw new CancellationException()
      }
    })
  }

  private static HttpUriRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    def request = new HttpUriRequest(method, uri)
    headers.entrySet().each {
      request.addHeader(new BasicHeader(it.key, it.value))
    }
    return request
  }

  @Override
  Integer statusOnRedirectError() {
    return 302
  }

  @Override
  boolean testRemoteConnection() {
    false // otherwise SocketTimeoutException for https requests
  }

  @Override
  boolean testCausality() {
    false
  }
}
