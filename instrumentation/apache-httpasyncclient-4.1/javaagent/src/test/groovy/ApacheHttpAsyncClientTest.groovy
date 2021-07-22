/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.AbstractHttpClientTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.util.concurrent.CancellationException
import org.apache.http.HttpResponse
import org.apache.http.client.config.RequestConfig
import org.apache.http.concurrent.FutureCallback
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.apache.http.message.BasicHeader
import spock.lang.AutoCleanup
import spock.lang.Shared

class ApacheHttpAsyncClientTest extends HttpClientTest<HttpUriRequest> implements AgentTestTrait {

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
  HttpUriRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    def request = new HttpUriRequest(method, uri)
    headers.entrySet().each {
      request.addHeader(new BasicHeader(it.key, it.value))
    }
    return request
  }

  @Override
  int sendRequest(HttpUriRequest request, String method, URI uri, Map<String, String> headers) {
    return client.execute(request, null).get().statusLine.statusCode
  }

  @Override
  void sendRequestWithCallback(HttpUriRequest request, String method, URI uri, Map<String, String> headers, AbstractHttpClientTest.RequestResult requestResult) {
    client.execute(request, new FutureCallback<HttpResponse>() {
      @Override
      void completed(HttpResponse httpResponse) {
        requestResult.complete(httpResponse.statusLine.statusCode)
      }

      @Override
      void failed(Exception e) {
        requestResult.complete(e)
      }

      @Override
      void cancelled() {
        throw new CancellationException()
      }
    })
  }

  @Override
  Integer responseCodeOnRedirectError() {
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

  @Override
  Set<AttributeKey<?>> httpAttributes(URI uri) {
    Set<AttributeKey<?>> extra = [
      SemanticAttributes.HTTP_SCHEME,
      SemanticAttributes.HTTP_TARGET
    ]
    super.httpAttributes(uri) + extra
  }
}
