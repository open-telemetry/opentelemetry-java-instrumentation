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
import khttp.KHttp

class KHttpClientTest extends HttpClientTest {

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    // khttp applies the same timeout for both connect and read
    def timeoutSeconds = CONNECT_TIMEOUT_MS / 1000
    def response = KHttp.request(method, uri.toString(), headers, Collections.emptyMap(), null, null, null, null, timeoutSeconds)
    if (callback != null) {
      callback.call()
    }
    return response.statusCode
  }

  @Override
  boolean testCircularRedirects() {
    return false
  }
}