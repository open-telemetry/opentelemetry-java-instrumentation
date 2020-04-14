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
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.apache.http.message.BasicHeader
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Timeout

import java.util.concurrent.Future

@Timeout(5)
class ApacheHttpAsyncClientNullCallbackTest extends HttpClientTest {

  @Shared
  RequestConfig requestConfig = RequestConfig.custom()
    .setConnectTimeout(CONNECT_TIMEOUT_MS)
    .setSocketTimeout(READ_TIMEOUT_MS)
    .build()

  @AutoCleanup
  @Shared
  def client = HttpAsyncClients.custom().setDefaultRequestConfig(requestConfig).build()

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
      callback()
    }
    return 200
  }

  @Override
  Integer statusOnRedirectError() {
    return 302
  }

  @Override
  boolean testRemoteConnection() {
    false // otherwise SocketTimeoutException for https requests
  }
}
