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
import com.sun.jersey.api.client.Client
import com.sun.jersey.api.client.ClientResponse
import com.sun.jersey.api.client.filter.GZIPContentEncodingFilter
import com.sun.jersey.api.client.filter.LoggingFilter
import io.opentelemetry.auto.test.base.HttpClientTest
import spock.lang.Shared
import spock.lang.Timeout

@Timeout(5)
class JaxRsClientV1Test extends HttpClientTest {

  @Shared
  Client client = Client.create()

  def setupSpec() {
    client.setConnectTimeout(CONNECT_TIMEOUT_MS)
    client.setReadTimeout(READ_TIMEOUT_MS)
    // Add filters to ensure spans aren't duplicated.
    client.addFilter(new LoggingFilter())
    client.addFilter(new GZIPContentEncodingFilter())
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def resource = client.resource(uri).requestBuilder
    headers.each { resource.header(it.key, it.value) }
    def body = BODY_METHODS.contains(method) ? "" : null
    ClientResponse response = resource.method(method, ClientResponse, body)
    callback?.call()

    return response.status
  }

  boolean testCircularRedirects() {
    false
  }
}
