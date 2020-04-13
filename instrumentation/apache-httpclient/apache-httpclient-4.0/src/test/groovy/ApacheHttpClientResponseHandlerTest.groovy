/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import io.opentelemetry.auto.test.base.HttpClientTest
import org.apache.http.HttpResponse
import org.apache.http.client.ResponseHandler
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.message.BasicHeader
import org.apache.http.params.HttpConnectionParams
import org.apache.http.params.HttpParams
import spock.lang.Shared
import spock.lang.Timeout

@Timeout(5)
class ApacheHttpClientResponseHandlerTest extends HttpClientTest {

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
    HttpConnectionParams.setSoTimeout(httpParams, READ_TIMEOUT_MS)
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
