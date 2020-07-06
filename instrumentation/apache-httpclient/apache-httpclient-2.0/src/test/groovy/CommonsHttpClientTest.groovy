/*
 * Copyright The OpenTelemetry Authors
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
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.HttpMethod
import org.apache.commons.httpclient.methods.DeleteMethod
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.httpclient.methods.HeadMethod
import org.apache.commons.httpclient.methods.OptionsMethod
import org.apache.commons.httpclient.methods.PostMethod
import org.apache.commons.httpclient.methods.PutMethod
import org.apache.commons.httpclient.methods.TraceMethod
import spock.lang.Shared
import spock.lang.Timeout

@Timeout(5)
class CommonsHttpClientTest extends HttpClientTest {
  @Shared
  HttpClient client = new HttpClient()

  def setupSpec() {
    client.setConnectionTimeout(CONNECT_TIMEOUT_MS)
    client.setTimeout(READ_TIMEOUT_MS)
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    HttpMethod httpMethod

    switch (method) {
      case "GET":
        httpMethod = new GetMethod(uri.toString())
        break
      case "PUT":
        httpMethod = new PutMethod(uri.toString())
        break
      case "POST":
        httpMethod = new PostMethod(uri.toString())
        break
      case "HEAD":
        httpMethod = new HeadMethod(uri.toString())
        break
      case "DELETE":
        httpMethod = new DeleteMethod(uri.toString())
        break
      case "OPTIONS":
        httpMethod = new OptionsMethod(uri.toString())
        break
      case "TRACE":
        httpMethod = new TraceMethod(uri.toString())
        break
      default:
        throw new RuntimeException("Unsupported method: " + method)
    }

    headers.each { httpMethod.setRequestHeader(it.key, it.value) }

    try {
      client.executeMethod(httpMethod)
      callback?.call()
      return httpMethod.getStatusCode()
    } finally {
      httpMethod.releaseConnection()
    }
  }

  @Override
  boolean testRedirects() {
    // Generates 4 spans
    false
  }
}
