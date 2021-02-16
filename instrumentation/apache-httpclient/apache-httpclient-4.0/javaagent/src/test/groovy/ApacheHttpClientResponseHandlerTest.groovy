/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import org.apache.http.HttpResponse
import org.apache.http.client.ResponseHandler
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.message.BasicHeader
import org.apache.http.params.HttpConnectionParams
import org.apache.http.params.HttpParams
import spock.lang.Shared
import spock.lang.Timeout

@Timeout(5)
class ApacheHttpClientResponseHandlerTest extends HttpClientTest implements AgentTestTrait {

  @Shared
  def client = new DefaultHttpClient()

  @Shared
  def handler = new ResponseHandler<Integer>() {
    @Override
    Integer handleResponse(HttpResponse response) {
      return response.statusLine.statusCode
    }
  }

  def setupSpec() {
    HttpParams httpParams = client.getParams()
    HttpConnectionParams.setConnectionTimeout(httpParams, CONNECT_TIMEOUT_MS)
  }

  @Override
  boolean testCausality() {
    return false
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def request = new HttpUriRequest(method, uri)
    headers.entrySet().each {
      request.addHeader(new BasicHeader(it.key, it.value))
    }

    def status = client.execute(request, handler)

    // handler execution is included within the client span, so we can't call the callback there.
    callback?.call()

    return status
  }
}
